import { injectable, inject } from 'inversify';
import { Workspace, Languages, LanguageClientFactory } from '@theia/languages/lib/browser';
import {
    SPRING_BOOT_SERVER_ID,
    SPRING_BOOT_SERVER_NAME,
    BOOT_PROPERTIES_YAML_LANGUAGE_ID,
    BOOT_PROPERTIES_LANGUAGE_ID
} from '../common';
import { DocumentSelector } from '@theia/languages/lib/common';
import { JAVA_LANGUAGE_ID } from '@theia/java/lib/common';
import {HighlightParams, HighlightService} from './highlight-service';
import { BootConfiguration, BootPreferences } from './boot-preferences';
import { StsLanguageClientContribution } from '@pivotal-tools/theia-languageclient/lib/browser/language-client-contribution';
import { ClasspathService } from '@pivotal-tools/theia-languageclient/lib/browser/classpath-service';
import { NotificationType } from 'vscode-jsonrpc';
import { HighlightCodeLensService } from './codelens-service';

const HIGHLIGHTS_NOTIFICATION_TYPE = new NotificationType<HighlightParams,void>('sts/highlight');

@injectable()
export class SpringBootClientContribution extends StsLanguageClientContribution<BootConfiguration> {

    readonly id = SPRING_BOOT_SERVER_ID;
    readonly name = SPRING_BOOT_SERVER_NAME;

    constructor(
        @inject(Workspace) workspace: Workspace,
        @inject(Languages) languages: Languages,
        @inject(LanguageClientFactory) languageClientFactory: LanguageClientFactory,
        @inject(HighlightService) protected readonly highlightService: HighlightService,
        @inject(HighlightCodeLensService) protected readonly highlightCodeLensService,
        @inject(ClasspathService) protected readonly classpathService: ClasspathService,
        @inject(BootPreferences) protected readonly preferences: BootPreferences
    ) {
        super(workspace, languages, languageClientFactory);
    }

    protected attachMessageHandlers() {
        super.attachMessageHandlers();
        this.languageClient.then(client => {
            client.onNotification(HIGHLIGHTS_NOTIFICATION_TYPE, (params) => {
                this.highlightService.handle(params);
                this.highlightCodeLensService.handleProtocols(params);
            });
            // this.classpathService.attach(client);
        });
    }

    protected get documentSelector(): DocumentSelector | undefined {
        return [JAVA_LANGUAGE_ID, BOOT_PROPERTIES_YAML_LANGUAGE_ID, BOOT_PROPERTIES_LANGUAGE_ID];
    }

    protected get globPatterns() {
        return [
            '**/*.java',
            '**/application*.yml',
            '**/bootstrap*.yml',
            '**/application*.properties',
            '**/bootstrap*.properties'
        ];
    }

}
