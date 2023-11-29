import { MICA_KIND_KIND, listEntities } from '../api/entity.ts';
import entityKindToIcon from '../components/entityKindToIcon.tsx';
import { MenuItem, Select } from '@mui/material';
import ListItemIcon from '@mui/material/ListItemIcon';

import { useQuery } from 'react-query';

interface Props {
    value: string | undefined;
    onChange: (value: string) => void;
}

const EntityKindSelect = ({ value, onChange }: Props) => {
    const { data, isFetching } = useQuery(
        ['entity', 'list', MICA_KIND_KIND],
        () => listEntities(undefined, undefined, MICA_KIND_KIND),
        {
            keepPreviousData: true,
            select: ({ data }) => data.sort((a, b) => a.name.localeCompare(b.name)),
        },
    );

    const effectiveValue = value ?? '';
    return (
        <Select
            label="Kind"
            disabled={isFetching}
            value={effectiveValue}
            onChange={(ev) => onChange(ev.target.value as string)}>
            <MenuItem key={''} value={''}>
                any
            </MenuItem>
            {data &&
                data.map((row) => (
                    <MenuItem key={row.id} value={row.name}>
                        <ListItemIcon>{entityKindToIcon(row.name)}</ListItemIcon>
                        {row.name}
                    </MenuItem>
                ))}
        </Select>
    );
};

export default EntityKindSelect;
