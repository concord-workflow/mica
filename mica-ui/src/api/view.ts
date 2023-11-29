import { doFetch, handleJsonResponse } from './common.ts';
import { EntityWithData } from './entity.ts';

export const render = (viewId: string): Promise<EntityWithData> =>
    doFetch(`/api/mica/v1/view/${viewId}/render`).then(handleJsonResponse<EntityWithData>);
