import { useMutation } from 'react-query';
import { UseMutationOptions } from 'react-query/types/react/types';

export interface ImportResponse {}

const importData = (file: File): Promise<ImportResponse> =>
    fetch('/api/mica/v1/document/import', {
        method: 'POST',
        body: file,
    }).then((resp) => resp.json());

export const useImportData = (
    options?: UseMutationOptions<ImportResponse, Error, { file: File }>,
) => useMutation<ImportResponse, Error, { file: File }>(({ file }) => importData(file), options);
