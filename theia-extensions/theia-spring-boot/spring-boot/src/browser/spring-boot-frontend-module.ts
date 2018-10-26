import '../../images/boot-icon.png';
import { SpringBootClientContribution} from './language-client-contribution';
import { LanguageClientContribution } from '@theia/languages/lib/browser';
import { ContainerModule } from 'inversify';
import './monaco-yaml-contribution';
import './monaco-properties-contribution';
import { HighlightService } from './highlight-service';
import { bindBootPreferences } from './boot-preferences';
import { HighlightCodeLensService } from './codelens-service';

export default new ContainerModule(bind => {
    // add your contribution bindings here
    bindBootPreferences(bind);
    bind(LanguageClientContribution).to(SpringBootClientContribution).inSingletonScope();
    bind(HighlightService).toSelf().inSingletonScope();
    bind(HighlightCodeLensService).toSelf().inSingletonScope();
});