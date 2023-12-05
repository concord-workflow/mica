import ChecklistIcon from '@mui/icons-material/Checklist';
import DataObjectIcon from '@mui/icons-material/DataObject';
import TableChartIcon from '@mui/icons-material/TableChart';

const entityKindToIcon = (kind: string) => {
    switch (kind) {
        case '/mica/kind/v1':
            return <ChecklistIcon fontSize="small" />;
        case '/mica/view/v1':
            return <TableChartIcon fontSize="small" />;
    }
    return <DataObjectIcon fontSize="small" />;
};

export default entityKindToIcon;
