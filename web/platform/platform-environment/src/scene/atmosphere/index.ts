/**
 * 大气装配层：把环境内核算出的数字**翻译**成天穹、光与雾。
 *
 * 它与 `player/` 是同一类东西 —— 场景的**可选能力**：
 * 需要天光的场景（室外）挂上它，不需要的（室内、太空）不挂，天然退出。
 */
export { createAtmosphereRig, type AtmosphereRigConfig } from './rig';
