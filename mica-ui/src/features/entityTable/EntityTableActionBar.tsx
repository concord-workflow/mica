import ActionBar from '../../components/ActionBar';
import SearchField from '../../components/SearchField.tsx';
import Spacer from '../../components/Spacer.tsx';
import CreateEntityButton from '../CreateEntityButton.tsx';
import EntityKindSelect from '../EntityKindSelect.tsx';
import CloudUploadIcon from '@mui/icons-material/CloudUpload';
import DownloadIcon from '@mui/icons-material/Download';
import { Button, FormControl, InputLabel, Tooltip } from '@mui/material';

interface Props {
    canCreate: boolean;
    canDownload: boolean;
    selectedPath: string;
    search: string;
    handleSearch: (value: string) => void;
    selectedKind: string | undefined;
    handleKindSelect: (value: string) => void;
    handleUpload?: () => void;
}

const EntityTableActionBar = ({
    canCreate,
    canDownload,
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
            {canDownload && (
                <Tooltip title="Download the current folder as a ZIP archive">
                    <FormControl>
                        <Button
                            variant="outlined"
                            startIcon={<DownloadIcon />}
                            target="_blank"
                            href={`/api/mica/ui/downloadFolder?namePrefix=${selectedPath}`}>
                            Download
                        </Button>
                    </FormControl>
                </Tooltip>
            )}
            <Spacer />
            <FormControl size="small" sx={{ minWidth: 200 }}>
                <InputLabel>Kind</InputLabel>
                <EntityKindSelect value={selectedKind} onChange={handleKindSelect} />
            </FormControl>
            <SearchField placeholder="Find by name" value={search} onChange={handleSearch} />
        </ActionBar>
    );
};

export default EntityTableActionBar;
