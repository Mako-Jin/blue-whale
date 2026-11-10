// 3D 查看器页：左实体树 + 右 three.js 画布（whale-common-three 首个业务消费方）
//
// 渲染规则：
//   - 有 modelFileId 的实体：从 /file/{id}/preview 拉流为 Blob → GLTFLoader 加载；
//   - 无模型的实体：占位立方体（按实体名标注区分）；
//   - transformJson 约定：{"position":[x,y,z],"rotation":[x,y,z],"scale":[x,y,z]}
//     （rotation 为弧度欧拉角；缺省分量按 0/1 处理）；
//   - 相机：OrbitControls（three/addons），鼠标拖拽旋转、滚轮缩放。

import { useCallback, useEffect, useRef, useState } from 'react';
import { App, Button, Drawer, Form, Input, InputNumber, Popconfirm, Select, Space, Tree } from 'antd';
import type { DataNode } from 'antd/es/tree';
import { WhaleSceneManager, loadGltf } from '@bluewhale/whale-common-three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import { Box3, BoxGeometry, Group, Mesh, MeshStandardMaterial, Object3D, Vector3 } from 'three';
import { entityApi, fetchModelUrl, revokeModelUrl, sceneApi, type EntitySaveRequest, type EntityVO, type SceneVO } from '../api/twin';

/** 变换 JSON 约定结构（分量可缺省） */
interface TransformJson {
  position?: [number, number, number];
  rotation?: [number, number, number];
  scale?: [number, number, number];
}

/** 解析变换 JSON（非法/为空返回缺省值） */
function parseTransform(json: string | null | undefined): TransformJson {
  if (!json) return {};
  try {
    const parsed = JSON.parse(json) as TransformJson;
    return typeof parsed === 'object' && parsed !== null ? parsed : {};
  } catch {
    return {};
  }
}

/** 把变换 JSON 应用到 three 对象（缺省分量不覆盖） */
function applyTransform(target: Object3D, json: string | null | undefined): void {
  const transform = parseTransform(json);
  if (transform.position) target.position.set(...transform.position);
  if (transform.rotation) target.rotation.set(...transform.rotation);
  if (transform.scale) target.scale.set(...transform.scale);
}

/** antd 树节点装配（附加原始实体引用便于选中编辑） */
interface EntityNode extends DataNode {
  key: string;
  title: string;
  entity: EntityVO;
  children: EntityNode[];
}

function toTreeNodes(list: EntityVO[]): EntityNode[] {
  return list.map((entity) => ({
    key: entity.id,
    title: `${entity.name}（${entity.entityCode}）`,
    entity,
    children: toTreeNodes(entity.children ?? []),
  }));
}

/** 扁平化实体树（供父节点下拉选择；树节点内持有原始实体引用） */
function flatten(list: EntityNode[], depth = 0): Array<{ entity: EntityVO; depth: number }> {
  const result: Array<{ entity: EntityVO; depth: number }> = [];
  for (const node of list) {
    result.push({ entity: node.entity, depth });
    result.push(...flatten(node.children ?? [], depth + 1));
  }
  return result;
}

/** 表单值（与 EntitySaveRequest 对齐；parentId/modelFileId 字符串化传输） */
interface EntityFormValues {
  entityCode: string;
  name: string;
  parentId?: string | null;
  modelFileId?: string | null;
  transformJson?: string;
  sortOrder?: number;
}

export default function ViewerPage({ initialSceneId }: { initialSceneId: string | null }) {
  const { message } = App.useApp();
  const containerRef = useRef<HTMLDivElement | null>(null);
  const managerRef = useRef<WhaleSceneManager | null>(null);
  const controlsRef = useRef<OrbitControls | null>(null);
  const rootGroupRef = useRef<Group | null>(null);
  const objectUrlsRef = useRef<string[]>([]);

  const [scenes, setScenes] = useState<SceneVO[]>([]);
  const [sceneId, setSceneId] = useState<string | null>(initialSceneId);
  const [tree, setTree] = useState<EntityNode[]>([]);
  const [selected, setSelected] = useState<EntityVO | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [loadingModel, setLoadingModel] = useState(false);
  const [form] = Form.useForm<EntityFormValues>();

  // 1) 场景下拉数据（含从场景页带入的初始场景）
  useEffect(() => {
    sceneApi
      .page({ current: 1, size: 100 })
      .then((page) => setScenes(page.records ?? []))
      .catch((error) => message.error(`加载场景列表失败：${String(error)}`));
  }, [message]);

  // 2) 实体树
  const loadTree = useCallback(async (id: string) => {
    try {
      const data = await entityApi.tree(id);
      setTree(toTreeNodes(data ?? []));
    } catch (error) {
      message.error(`加载实体树失败：${String(error)}`);
    }
  }, [message]);

  // 3) three 初始化（挂载一次；容器随页面布局给满高度）
  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;
    const manager = new WhaleSceneManager({ container, backgroundColor: 0x1a2b3c }).init();
    manager.getCamera().position.set(6, 5, 8);
    const controls = new OrbitControls(manager.getCamera(), manager.getRenderer().domElement);
    controls.enableDamping = true;
    const root = new Group();
    manager.getScene().add(root);
    manager.onFrame(() => controls.update());
    manager.start();
    managerRef.current = manager;
    controlsRef.current = controls;
    rootGroupRef.current = root;
    return () => {
      manager.dispose();
      managerRef.current = null;
      controlsRef.current = null;
      rootGroupRef.current = null;
    };
  }, []);

  // 4) 场景变化 → 重建场景内容（先清空旧对象与 objectURL）
  const buildSceneObjects = useCallback(async (id: string) => {
    const manager = managerRef.current;
    const root = rootGroupRef.current;
    if (!manager || !root) return;
    setLoadingModel(true);
    // 清理旧对象
    for (const url of objectUrlsRef.current) revokeModelUrl(url);
    objectUrlsRef.current = [];
    root.clear();
    try {
      const flat = await entityApi.list(id);
      const placeholder = new BoxGeometry(1, 1, 1);
      for (const entity of flat) {
        const holder = new Group();
        holder.name = `${entity.entityCode}:${entity.name}`;
        if (entity.modelFileId) {
          try {
            const url = await fetchModelUrl(entity.modelFileId);
            objectUrlsRef.current.push(url);
            const gltf = await loadGltf(url);
            holder.add(gltf.scene);
          } catch {
            // 模型拉取失败降级为占位体，不阻断其余实体
            holder.add(new Mesh(placeholder, new MeshStandardMaterial({ color: 0x8b0000 })));
          }
        } else {
          holder.add(new Mesh(placeholder, new MeshStandardMaterial({ color: 0x4c8dbf })));
        }
        applyTransform(holder, entity.transformJson);
        root.add(holder);
      }
      // 相机对准场景包围盒
      const box = new Box3().setFromObject(root);
      if (!box.isEmpty()) {
        const center = box.getCenter(new Vector3());
        const size = box.getSize(new Vector3()).length();
        const camera = manager.getCamera();
        camera.position.set(center.x + size, center.y + size * 0.6, center.z + size);
        controlsRef.current?.target.copy(center);
        controlsRef.current?.update();
      }
    } catch (error) {
      message.error(`装配 3D 场景失败：${String(error)}`);
    } finally {
      setLoadingModel(false);
    }
  }, [message]);

  useEffect(() => {
    if (!sceneId) return;
    void loadTree(sceneId);
    void buildSceneObjects(sceneId);
  }, [sceneId, loadTree, buildSceneObjects]);

  // 卸载时释放 objectURL
  useEffect(() => () => {
    for (const url of objectUrlsRef.current) revokeModelUrl(url);
    objectUrlsRef.current = [];
  }, []);

  // 5) 实体编辑抽屉
  const openCreate = (parentId: string | null) => {
    setSelected(null);
    form.resetFields();
    form.setFieldsValue({ parentId: parentId ?? null, sortOrder: 0 });
    setDrawerOpen(true);
  };

  const openEdit = (entity: EntityVO) => {
    setSelected(entity);
    form.setFieldsValue({
      entityCode: entity.entityCode,
      name: entity.name,
      parentId: entity.parentId ?? null,
      modelFileId: entity.modelFileId ?? null,
      transformJson: entity.transformJson ?? undefined,
      sortOrder: entity.sortOrder,
    });
    setDrawerOpen(true);
  };

  const submit = async () => {
    if (!sceneId) return;
    const values = await form.validateFields();
    const body: EntitySaveRequest = {
      entityCode: (values.entityCode ?? '').trim(),
      name: (values.name ?? '').trim(),
      parentId: values.parentId ? Number(values.parentId) : null,
      modelFileId: values.modelFileId ? Number(values.modelFileId) : null,
      transformJson: values.transformJson?.trim() || null,
      sortOrder: values.sortOrder ?? 0,
    };
    try {
      if (selected) {
        await entityApi.update(sceneId, selected.id, body);
        message.success('实体已更新');
      } else {
        await entityApi.create(sceneId, body);
        message.success('实体已创建');
      }
      setDrawerOpen(false);
      void loadTree(sceneId);
      void buildSceneObjects(sceneId);
    } catch (error) {
      message.error(`保存失败：${String(error)}`);
    }
  };

  const removeEntity = async (entity: EntityVO) => {
    if (!sceneId) return;
    try {
      await entityApi.remove(sceneId, entity.id);
      message.success('实体已删除');
      setSelected(null);
      void loadTree(sceneId);
      void buildSceneObjects(sceneId);
    } catch (error) {
      message.error(`删除失败：${String(error)}`);
    }
  };

  const flatEntities = flatten(tree);

  return (
    <div className="twin-viewer">
      <div className="twin-viewer-tree">
        <Space direction="vertical" style={{ width: '100%' }} size="small">
          <Select
            placeholder="选择场景"
            style={{ width: '100%' }}
            value={sceneId ?? undefined}
            options={scenes.map((scene) => ({ value: scene.id, label: `${scene.name}（${scene.code}）` }))}
            onChange={(value) => setSceneId(value)}
          />
          <Space>
            <Button size="small" type="primary" disabled={!sceneId} onClick={() => openCreate(null)}>
              新建根实体
            </Button>
            <Button size="small" disabled={!selected} onClick={() => selected && openEdit(selected)}>
              编辑选中
            </Button>
          </Space>
          {selected && (
            <Popconfirm title="确认删除选中实体？" onConfirm={() => void removeEntity(selected)}>
              <Button size="small" danger>
                删除选中
              </Button>
            </Popconfirm>
          )}
        </Space>
        <Tree<EntityNode>
          style={{ marginTop: 12 }}
          treeData={tree}
          defaultExpandAll
          selectedKeys={selected ? [selected.id] : []}
          onSelect={(keys) => {
            const key = keys[0];
            if (!key) return;
            const found = flatEntities.find((item) => item.entity.id === key);
            if (found) setSelected(found.entity);
          }}
        />
      </div>
      <div className="twin-viewer-canvas">
        <div ref={containerRef} style={{ width: '100%', height: '100%' }} />
        <div className="twin-viewer-hint">
          {loadingModel ? '模型加载中…' : '左键旋转 · 滚轮缩放 · 右键平移'}
        </div>
      </div>
      <Drawer
        title={selected ? '编辑实体' : '新建实体'}
        width={440}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        extra={
          <Space>
            <Button onClick={() => setDrawerOpen(false)}>取消</Button>
            <Button type="primary" onClick={() => void submit()}>
              保存
            </Button>
          </Space>
        }
      >
        <Form<EntityFormValues> form={form} layout="vertical">
          <Form.Item
            name="entityCode"
            label="编码"
            rules={[
              { required: true, message: '请输入实体编码' },
              { pattern: /^[a-zA-Z][a-zA-Z0-9_-]{0,63}$/, message: '字母开头的字母/数字/下划线/中划线' },
            ]}
            extra={selected ? '编码创建后不可修改' : '场景内唯一，如 boiler-01'}
          >
            <Input disabled={!!selected} placeholder="boiler-01" />
          </Form.Item>
          <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入实体名称' }]}>
            <Input placeholder="1号锅炉" maxLength={128} />
          </Form.Item>
          <Form.Item name="parentId" label="父节点">
            <Select
              allowClear
              placeholder="无（作为根节点）"
              options={flatEntities
                .filter((item) => item.entity.id !== selected?.id)
                .map((item) => ({
                  value: item.entity.id,
                  label: `${'　'.repeat(item.depth)}${item.entity.name}`,
                }))}
            />
          </Form.Item>
          <Form.Item name="modelFileId" label="模型文件ID" extra="whale-file 文件ID（GLB/GLTF），留空显示占位体">
            <InputNumber style={{ width: '100%' }} min={1} precision={0} placeholder="文件ID" />
          </Form.Item>
          <Form.Item
            name="transformJson"
            label="变换 JSON"
            rules={[
              {
                validator: (_, value: string | undefined) => {
                  if (!value) return Promise.resolve();
                  try {
                    JSON.parse(value);
                    return Promise.resolve();
                  } catch {
                    return Promise.reject(new Error('不是合法的 JSON'));
                  }
                },
              },
            ]}
            extra='{"position":[0,0,0],"rotation":[0,0,0],"scale":[1,1,1]}（rotation 弧度）'
          >
            <Input.TextArea rows={3} placeholder='{"position":[0,0,0]}' />
          </Form.Item>
          <Form.Item name="sortOrder" label="排序号">
            <InputNumber style={{ width: 160 }} precision={0} />
          </Form.Item>
        </Form>
      </Drawer>
    </div>
  );
}
