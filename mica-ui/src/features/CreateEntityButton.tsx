import { MICA_KIND_KIND, listEntities } from '../api/entity.ts';
import entityKindToIcon from '../components/entityKindToIcon.tsx';
import AddIcon from '@mui/icons-material/Add';
import { Button, Menu, MenuItem } from '@mui/material';
import ListItemIcon from '@mui/material/ListItemIcon';

import { useQuery } from '@tanstack/react-query';
import React from 'react';
import { useNavigate } from 'react-router-dom';

interface Props {
    path: string;
}

const CreateEntityButton = ({ path }: Props) => {
    const navigate = useNavigate();

    const { data, isFetching } = useQuery({
        queryKey: ['entity', 'list', '/', MICA_KIND_KIND],
        queryFn: () => listEntities({ entityKind: MICA_KIND_KIND }),
        select: ({ data }) => data.sort((a, b) => a.name.localeCompare(b.name)),
        placeholderData: (prev) => prev,
    });
    const [anchorEl, setAnchorEl] = React.useState<null | HTMLElement>(null);

    const handleClose = React.useCallback(() => {
        setAnchorEl(null);
    }, []);

    const handleClick = React.useCallback(
        (kind: string, name: string) => {
            localStorage.removeItem(`dirty-_new`);
            navigate(
                `/entity/_new/edit?kind=${encodeURIComponent(kind)}&name=${encodeURIComponent(
                    name,
                )}`,
            );
        },
        [navigate],
    );

    return (
        <>
            <Button
                startIcon={<AddIcon />}
                variant="contained"
                onClick={(ev) => setAnchorEl(ev.currentTarget)}>
                Create
            </Button>
            <Menu anchorEl={anchorEl} open={Boolean(anchorEl)} onClose={handleClose}>
                {(isFetching || !data) && <MenuItem disabled>Loading...</MenuItem>}
                {!isFetching &&
                    data &&
                    data.map((row) => (
                        <MenuItem
                            key={row.id}
                            onClick={() =>
                                handleClick(row.name, (path !== '/' ? path : '') + '/myEntity')
                            }>
                            <ListItemIcon>{entityKindToIcon(row.name)}</ListItemIcon>
                            {row.name}
                        </MenuItem>
                    ))}
            </Menu>
        </>
    );
};

export default CreateEntityButton;
