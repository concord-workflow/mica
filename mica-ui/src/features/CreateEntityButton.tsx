import {
    EntityList,
    MICA_DASHBOARD_KIND,
    MICA_KIND_KIND,
    MICA_VIEW_KIND,
    listEntities,
} from '../api/entity.ts';
import entityKindToIcon from '../components/entityKindToIcon.tsx';
import AddIcon from '@mui/icons-material/Add';
import { Button, ListSubheader, Menu, MenuItem, Tooltip } from '@mui/material';
import ListItemIcon from '@mui/material/ListItemIcon';

import { useQuery } from '@tanstack/react-query';
import React from 'react';
import { useNavigate } from 'react-router-dom';

interface KindToPick {
    name: string;
    label: string;
    tooltip?: string;
}

const STANDARD_KINDS: Array<KindToPick> = [
    {
        name: MICA_KIND_KIND,
        label: 'Kind',
        tooltip: "Create new entity that can be used as a 'kind' in other entities.",
    },
    {
        name: MICA_VIEW_KIND,
        label: 'View',
        tooltip: 'Create a new view to fetch, aggregate and filter data from other entities.',
    },
    {
        name: MICA_DASHBOARD_KIND,
        label: 'Dashboard',
        tooltip: 'Create a new dashboard entity to visualize data rendered using views.',
    },
];

interface Props {
    path: string;
}

const CreateEntityButton = ({ path }: Props) => {
    const navigate = useNavigate();

    const { data, isFetching } = useQuery<EntityList, Error, Array<KindToPick>>({
        queryKey: ['entity', 'list', '/', MICA_KIND_KIND],
        queryFn: () => listEntities({ entityKind: MICA_KIND_KIND }),
        select: ({ data }) =>
            data
                .filter((e) => STANDARD_KINDS.find((k) => k.name === e.name) === undefined)
                .sort((a, b) => a.name.localeCompare(b.name))
                .map(({ name }) => ({ name, label: name })),
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

    const KindItem = ({ row }: { row: KindToPick }) => (
        <Tooltip title={row.tooltip}>
            <MenuItem
                onClick={() => handleClick(row.name, (path !== '/' ? path : '') + '/myEntity')}>
                <ListItemIcon>{entityKindToIcon(row.name)}</ListItemIcon>
                {row.label}
            </MenuItem>
        </Tooltip>
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
                {!isFetching && (
                    <>
                        <ListSubheader>Standard entities</ListSubheader>
                        {STANDARD_KINDS.map((row) => (
                            <KindItem key={row.name} row={row} />
                        ))}
                    </>
                )}
                {!isFetching && data && (
                    <>
                        <ListSubheader>Custom kinds</ListSubheader>
                        {data.map((row) => (
                            <KindItem key={row.name} row={row} />
                        ))}
                    </>
                )}
            </Menu>
        </>
    );
};

export default CreateEntityButton;
