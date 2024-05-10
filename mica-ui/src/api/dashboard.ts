import { doFetch, handleJsonResponse } from './common.ts';

export interface MicaDashboardV1 {
    title: string;
    layout: Layout;
    table?: TableLayout;
}

export enum Layout {
    TABLE = 'TABLE',
}

export interface TableLayout {
    columns: Array<TableColumnDef>;
}

export interface TableColumnDef {
    title: string;
    jsonPath: string;
}

export interface DashboardRenderResponse {
    dashboard: MicaDashboardV1;
    data: Array<object>;
}

export const render = (entityId: string): Promise<DashboardRenderResponse> =>
    doFetch(`/api/mica/v1/dashboard/${entityId}`).then(handleJsonResponse<DashboardRenderResponse>);
