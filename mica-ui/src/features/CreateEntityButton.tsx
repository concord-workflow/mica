import { MICA_KIND_KIND, listEntities } from '../api/entity.ts';
import entityKindToIcon from '../components/entityKindToIcon.tsx';
import AddIcon from '@mui/icons-material/Add';
import { Button, Menu, MenuItem } from '@mui/material';
import ListItemIcon from '@mui/material/ListItemIcon';

import React from 'react';
import { useQuery } from 'react-query';
import { useNavigate } from 'react-router-dom';

const CreateEntityButton = () => {
    const navigate = useNavigate();

    const { data, isFetching } = useQuery(
        ['entity', 'list', '/', MICA_KIND_KIND],
        () => listEntities({ entityKind: MICA_KIND_KIND }),
        {
            keepPreviousData: true,
            select: ({ data }) => data.sort((a, b) => a.name.localeCompare(b.name)),
        },
    );
    const [anchorEl, setAnchorEl] = React.useState<null | HTMLElement>(null);

    const handleClose = () => {
        setAnchorEl(null);
    };

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
                            onClick={() => navigate(`/entity/_new/edit?kind=${row.name}`)}>
                            <ListItemIcon>{entityKindToIcon(row.name)}</ListItemIcon>
                            {row.name}
                        </MenuItem>
                    ))}
            </Menu>
        </>
    );
};

export default CreateEntityButton;
