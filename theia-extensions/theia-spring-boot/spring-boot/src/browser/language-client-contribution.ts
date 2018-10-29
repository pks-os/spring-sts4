/// <reference types='@typefox/monaco-editor-core/monaco'/>

import { injectable, inject } from 'inversify';
import { Workspace, Languages, LanguageClientFactory } from '@theia/languages/lib/browser';
import {
    SPRING_BOOT_SERVER_ID,
    SPRING_BOOT_SERVER_NAME,
    BOOT_PROPERTIES_YAML_LANGUAGE_ID,
    BOOT_PROPERTIES_LANGUAGE_ID
} from '../common';
import { DocumentSelector } from '@theia/languages/lib/browser';
import { JAVA_LANGUAGE_ID } from '@theia/java/lib/common';
import { HighlightService} from './highlight-service';
import {BootConfiguration, BootPreferences, CODELENS_PREF_NAME, HIGHLIGHTS_PREF_NAME} from './boot-preferences';
import { StsLanguageClientContribution } from '@pivotal-tools/theia-languageclient/lib/browser/language-client-contribution';
import { ClasspathService } from '@pivotal-tools/theia-languageclient/lib/browser/classpath-service';
import { HighlightCodeLensService } from './codelens-service';
import {Disposable} from '@theia/core';
import {OpenerService} from '@theia/core/lib/browser';
import URI from '@theia/core/lib/common/uri';

const HIGHLIGHTS_NOTIFICATION_TYPE = 'sts/highlight';

@injectable()
export class SpringBootClientContribution extends StsLanguageClientContribution<BootConfiguration> {

    readonly id = SPRING_BOOT_SERVER_ID;
    readonly name = SPRING_BOOT_SERVER_NAME;

    private codeLensProviderRegistration: Disposable = null;

    constructor(
        @inject(Workspace) workspace: Workspace,
        @inject(Languages) languages: Languages,
        @inject(LanguageClientFactory) languageClientFactory: LanguageClientFactory,
        @inject(HighlightService) protected readonly highlightService: HighlightService,
        @inject(HighlightCodeLensService) protected readonly highlightCodeLensService,
        @inject(ClasspathService) protected readonly classpathService: ClasspathService,
        @inject(BootPreferences) protected readonly preferences: BootPreferences,
        @inject(OpenerService) private readonly openerService: OpenerService
    ) {
        super(workspace, languages, languageClientFactory);
    }

    protected async attachMessageHandlers() {
        super.attachMessageHandlers();
        const client = await this.languageClient;
            client.onNotification(HIGHLIGHTS_NOTIFICATION_TYPE, (params) => {
                this.highlightService.handle(params);
                if (this.preferences[CODELENS_PREF_NAME]) {
                    this.highlightCodeLensService.handle(params);
                }
            });
            // this.classpathService.attach(client);

            this.preferences.onPreferenceChanged(event => {
                if (event.preferenceName === CODELENS_PREF_NAME
                    || event.preferenceName === HIGHLIGHTS_PREF_NAME) {
                    this.toggleHighlightCodeLenses();
                }
            });
            this.toggleHighlightCodeLenses();
    }

    private toggleHighlightCodeLenses() {
        if (this.preferences[CODELENS_PREF_NAME] && this.preferences[HIGHLIGHTS_PREF_NAME]) {
            if (!this.codeLensProviderRegistration) {
                this.codeLensProviderRegistration = monaco.languages.registerCodeLensProvider(JAVA_LANGUAGE_ID, this.highlightCodeLensService);
            }
        } else {
            if (this.codeLensProviderRegistration) {
                this.codeLensProviderRegistration.dispose();
                this.codeLensProviderRegistration = null;
            }
        }
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

    activate(): Disposable {
        const disposable = super.activate();

        const commandRegistration = this.registry.registerCommand({
            id: 'sts.open.url'
        }, {
            execute: (url: string) => {
                if (url) {
                    const uri = new URI(url);
                    if (uri) {
                        this.openerService.getOpener(uri).then(handler => handler.open(uri));
                    }
                }
            }
        });

        return {
            dispose: () => {
                if (this.codeLensProviderRegistration) {
                    this.codeLensProviderRegistration.dispose();
                }
                commandRegistration.dispose();
                disposable.dispose();
            }
        };

    }

}

