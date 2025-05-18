import { Entry, EntryType } from '../../api/entityList.ts';
import entityKindToIcon from '../../components/entityKindToIcon.tsx';
import highlightSubstring from '../../components/highlight.tsx';
import EntityTableRowMenu from './EntityTableRowMenu.tsx';
import FolderIcon from '@mui/icons-material/Folder';
import { Box, Link, TableCell, TableRow, Tooltip, Typography } from '@mui/material';

import { Link as RouterLink } from 'react-router-dom';

interface Props {
    pathPrefix: string;
    row: Entry;
    search: string;
    handleDelete?: (row: Entry) => void;
    selectedPath: string;
}

const EntityTableRow = ({ pathPrefix, row, search, handleDelete, selectedPath }: Props) => {
    return (
        <TableRow>
            <TableCell>
                {row.type === EntryType.FOLDER && (
                    <Tooltip title="Folder">
                        <FolderIcon />
                    </Tooltip>
                )}
                {row.entityKind && (
                    <Tooltip title={row.entityKind}>{entityKindToIcon(row.entityKind)}</Tooltip>
                )}
            </TableCell>
            <TableCell>
                {row.type === EntryType.FOLDER && (
                    <Link
                        component={RouterLink}
                        to={`${pathPrefix}?path=${selectedPath}${
                            selectedPath === '/' ? '' : '/'
                        }${encodeURIComponent(row.name)}`}>
                        {highlightSubstring(row.name, search)}
                    </Link>
                )}
                {row.entityId && (
                    <Link component={RouterLink} to={`/entity/${row.entityId}/details`}>
                        {highlightSubstring(row.name, search)}
                    </Link>
                )}
                {row.deletedAt && (
                    <Box>
                        <Typography variant="caption">Deleted at {row.deletedAt}</Typography>
                    </Box>
                )}
            </TableCell>
            <TableCell align="right">
                {handleDelete && <EntityTableRowMenu handleDelete={handleDelete} row={row} />}
            </TableCell>
        </TableRow>
    );
};

export default EntityTableRow;
