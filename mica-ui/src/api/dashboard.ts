import { doFetch, handleJsonResponse } from './common.ts';
import { PartialEntity } from './entity.ts';
import { ApiError } from './error.ts';

import { UseMutationOptions, useMutation } from '@tanstack/react-query';

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
    data: Array<Array<string | boolean | number>>;
}

export const render = (entityId: string): Promise<DashboardRenderResponse> =>
    doFetch(`/api/mica/v1/dashboard/render/${entityId}`).then(
        handleJsonResponse<DashboardRenderResponse>,
    );

export interface PreviewDashboardRequest {
    dashboard: PartialEntity;
}

const preview = (request: PreviewDashboardRequest): Promise<DashboardRenderResponse> =>
    doFetch(`/api/mica/v1/dashboard/preview`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify(request),
    }).then(handleJsonResponse<DashboardRenderResponse>);

export const usePreview = (
    options?: UseMutationOptions<DashboardRenderResponse, ApiError, PreviewDashboardRequest>,
) =>
    useMutation<DashboardRenderResponse, ApiError, PreviewDashboardRequest>({
        mutationFn: preview,
        ...options,
    });
