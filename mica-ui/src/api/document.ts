import { handleJsonResponse } from './common.ts';

import { useMutation } from 'react-query';
import { UseMutationOptions } from 'react-query/types/react/types';

export interface ImportResponse {}

const importDocument = (file: File): Promise<ImportResponse> =>
    fetch('/api/mica/v1/document/import', {
        method: 'POST',
        body: file,
    }).then(handleJsonResponse<ImportResponse>);

export const useImportDocument = (
    options?: UseMutationOptions<ImportResponse, Error, { file: File }>,
) =>
    useMutation<ImportResponse, Error, { file: File }>(({ file }) => importDocument(file), options);
