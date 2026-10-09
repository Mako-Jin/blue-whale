import { createEslintConfig } from 'common-config/eslint';

/**
 * 工作区根 ESLint 配置。
 *
 * 存在的原因：ESLint 9+ 的 flat config 只从「当前工作目录」向上查找配置文件，
 * 不会按被检查文件的位置逐级查找。lint-staged 在 web/ 根目录执行
 * `eslint --fix`，因此这里必须有一份配置，否则它找不到规则。
 *
 * 规则本身仍然只维护一份，在 packages/common-config 里。
 */
export default createEslintConfig();
