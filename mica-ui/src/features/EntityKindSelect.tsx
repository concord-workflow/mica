import { MICA_KIND_KIND, listEntities } from '../api/entity.ts';
import entityKindToIcon from '../components/entityKindToIcon.tsx';
import { MenuItem, Select } from '@mui/material';
import ListItemIcon from '@mui/material/ListItemIcon';
import { SelectProps } from '@mui/material/Select/Select';

import { useQuery } from '@tanstack/react-query';

interface Props extends Omit<SelectProps, 'value' | 'onChange'> {
    value: string | undefined;
    onChange: (value: string) => void;
    disableAny?: boolean;
}

const EntityKindSelect = ({ value, onChange, disableAny, ...rest }: Props) => {
    const { data, isFetching } = useQuery({
        queryKey: ['entity', 'list', '/', MICA_KIND_KIND],
        queryFn: () => listEntities({ entityKind: MICA_KIND_KIND }),

        placeholderData: (prev) => prev,
        select: ({ data }) => data.sort((a, b) => a.name.localeCompare(b.name)),
    });

    const effectiveValue = value ?? '';
    return (
        <Select
            label="Kind"
            disabled={isFetching}
            value={effectiveValue}
            onChange={(ev) => onChange(ev.target.value as string)}
            {...rest}>
            {!disableAny && (
                <MenuItem key={''} value={''}>
                    any
                </MenuItem>
            )}
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
