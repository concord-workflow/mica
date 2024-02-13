import { getHistoryDoc } from '../../api/history.ts';
import { MONACO_OPTIONS } from '../editor/options.ts';
import { Button, Dialog, DialogActions, DialogContent, DialogTitle } from '@mui/material';

import Editor from '@monaco-editor/react';
import { useQuery } from 'react-query';

interface Props {
    open: boolean;
    onClose: () => void;
    entityId: string;
    updatedAt: string;
}

const ViewHistoryEntryPopup = ({ open, onClose, entityId, updatedAt }: Props) => {
    const { data, isFetching } = useQuery(
        ['history', entityId, updatedAt, 'doc'],
        () => getHistoryDoc(entityId, updatedAt),
        { enabled: open, keepPreviousData: false },
    );

    return (
        <Dialog open={open} onClose={onClose} fullWidth={true}>
            <DialogTitle>Previous version</DialogTitle>
            <DialogContent>
                <Editor
                    loading={isFetching}
                    height="300px"
                    defaultLanguage="yaml"
                    options={MONACO_OPTIONS}
                    value={data}
                />
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose}>Close</Button>
            </DialogActions>
        </Dialog>
    );
};

export default ViewHistoryEntryPopup;
