import ActionBar from '../../components/ActionBar';
import SearchField from '../../components/SearchField.tsx';
import Spacer from '../../components/Spacer.tsx';
import CreateEntityButton from '../CreateEntityButton.tsx';
import EntityKindSelect from '../EntityKindSelect.tsx';
import CloudUploadIcon from '@mui/icons-material/CloudUpload';
import { Button, FormControl, InputLabel } from '@mui/material';

interface Props {
    canCreate: boolean;
    selectedPath: string;
    search: string;
    handleSearch: (value: string) => void;
    selectedKind: string | undefined;
    handleKindSelect: (value: string) => void;
    handleUpload?: () => void;
}

const EntityTableActionBar = ({
    canCreate,
    selectedPath,
    handleUpload,
    selectedKind,
    handleKindSelect,
    search,
    handleSearch,
}: Props) => {
    return (
        <ActionBar sx={{ mb: 2 }}>
            {canCreate && (
                <FormControl>
                    <CreateEntityButton path={selectedPath} />
                </FormControl>
            )}
            {handleUpload && (
                <FormControl>
                    <Button
                        startIcon={<CloudUploadIcon />}
                        variant="outlined"
                        onClick={handleUpload}>
                        Upload
                    </Button>
                </FormControl>
            )}
            <Spacer />
            <FormControl size="small" sx={{ minWidth: 200 }}>
                <InputLabel>Kind</InputLabel>
                <EntityKindSelect value={selectedKind} onChange={handleKindSelect} />
            </FormControl>
            <SearchField placeholder="Filter by name" value={search} onChange={handleSearch} />
        </ActionBar>
    );
};

export default EntityTableActionBar;
