import { getHistoryDoc } from '../../api/history.ts';
import { MONACO_OPTIONS, modeToTheme } from '../editor/options.ts';
import {
    Button,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    useColorScheme,
} from '@mui/material';

import Editor from '@monaco-editor/react';
import { useQuery } from '@tanstack/react-query';

interface Props {
    open: boolean;
    onClose: () => void;
    entityId: string;
    updatedAt: string;
}

const ViewHistoryEntryPopup = ({ open, onClose, entityId, updatedAt }: Props) => {
    const { mode, systemMode } = useColorScheme();

    const { data, isFetching } = useQuery({
        queryKey: ['history', entityId, updatedAt, 'doc'],
        queryFn: () => getHistoryDoc(entityId, updatedAt),
        enabled: open,
        placeholderData: (prev) => prev,
    });

    return (
        <Dialog open={open} onClose={onClose} fullWidth={true}>
            <DialogTitle>Previous version</DialogTitle>
            <DialogContent>
                <Editor
                    loading={isFetching}
                    height="300px"
                    defaultLanguage="yaml"
                    options={{
                        ...MONACO_OPTIONS,
                        readOnly: true,
                        renderValidationDecorations: 'off',
                    }}
                    theme={modeToTheme(mode === 'system' ? systemMode : mode)}
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
