import { describe, expect, it } from 'vitest';
import { createMapClient, type MapRequest, type MapRequestOptions } from './client';

interface RecordedCall {
  path: string;
  options?: MapRequestOptions;
}

/** 构造「记录调用 + 返回固定结果」的请求函数 */
function recorder(result: unknown = null) {
  const calls: RecordedCall[] = [];
  const request: MapRequest = async <T>(path: string, options?: MapRequestOptions) => {
    calls.push({ path, options });
    return result as T;
  };
  return { calls, client: createMapClient(request) };
}

describe('createMapClient 路径与参数', () => {
  it('geocode 走 POST /map/geocode 且 body 原样透传', async () => {
    const { calls, client } = recorder({ lng: 1, lat: 2, coordType: 'GCJ02' });
    await client.geocode({ address: '天安门', city: '北京' });
    expect(calls[0].path).toBe('/map/geocode');
    expect(calls[0].options?.method).toBe('POST');
    expect(calls[0].options?.body).toEqual({ address: '天安门', city: '北京' });
  });

  it('searchPoi 走 GET 并把关键词放进 query', async () => {
    const { calls, client } = recorder({ items: [], total: 0, page: 1, size: 10, provider: 'tencent' });
    const page = await client.searchPoi('天安门', { city: '北京', page: 2, size: 20 });
    expect(calls[0].path).toBe('/map/poi/search');
    expect(calls[0].options?.method).toBe('GET');
    expect(calls[0].options?.query).toMatchObject({
      keyword: '天安门',
      city: '北京',
      page: 2,
      size: 20,
    });
    expect(page.total).toBe(0);
  });

  it('打点路径对 bizType/bizId 做 URL 编码（含斜杠也不会切断路径）', async () => {
    const { calls, client } = recorder(null);
    await client.saveLocation('CRM_CONTACT', 'a/b 1', { lng: 116.4, lat: 39.9, coordType: 'GCJ02' });
    expect(calls[0].path).toBe('/map/location/CRM_CONTACT/a%2Fb%201');
    expect(calls[0].options?.method).toBe('PUT');
  });

  it('取消打点走 POST .../remove', async () => {
    const { calls, client } = recorder(null);
    await client.removeLocation('CRM_CONTACT', 42);
    expect(calls[0].path).toBe('/map/location/CRM_CONTACT/42/remove');
    expect(calls[0].options?.method).toBe('POST');
  });

  it('附近查询把可选参数放进 query', async () => {
    const { calls, client } = recorder([]);
    await client.nearby({ lng: 116.4, lat: 39.9, bizType: 'OA_ATTEND', radiusMeters: 500, limit: 20 });
    expect(calls[0].path).toBe('/map/location/nearby');
    expect(calls[0].options?.query).toMatchObject({ radiusMeters: 500, limit: 20 });
  });

  it('围栏判定两个接口路径不同：contains 带 id，match 不带', async () => {
    const { calls, client } = recorder({ id: 1, inside: true, distanceMeters: 10 });
    await client.fenceContains(7, { lng: 116.4, lat: 39.9, coordType: 'GCJ02' });
    await client.fenceMatch({ lng: 116.4, lat: 39.9, coordType: 'GCJ02' });
    expect(calls[0].path).toBe('/map/fence/7/contains');
    expect(calls[1].path).toBe('/map/fence/match');
  });

  it('围栏分页走 GET /map/fence/page', async () => {
    const { calls, client } = recorder({ records: [], total: 0, size: 20, current: 1 });
    await client.pageFence({ bizType: 'OA_ATTEND', page: 1, size: 20 });
    expect(calls[0].path).toBe('/map/fence/page');
    expect(calls[0].options?.method).toBe('GET');
  });

  it('getLocation 未打点时返回 null（不抛异常）', async () => {
    const { client } = recorder(null);
    await expect(client.getLocation('CRM_CONTACT', 1)).resolves.toBeNull();
  });

  it('route 与 distance 都走 POST', async () => {
    const { calls, client } = recorder({});
    await client.route({
      originLng: 116.4,
      originLat: 39.9,
      destinationLng: 116.5,
      destinationLat: 39.95,
      mode: 'DRIVING',
    });
    await client.distance({ originLng: 116.4, originLat: 39.9, targets: [{ lng: 116.5, lat: 39.95 }] });
    expect(calls[0].path).toBe('/map/route');
    expect(calls[1].path).toBe('/map/distance');
    expect(calls[0].options?.method).toBe('POST');
    expect(calls[1].options?.method).toBe('POST');
  });
});
