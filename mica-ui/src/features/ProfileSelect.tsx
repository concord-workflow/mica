import { listClientProfiles } from '../api/profile.ts';
import { MenuItem, Select } from '@mui/material';

import { useQuery } from 'react-query';

interface Props {
    value: string | undefined;
    onChange: (profileId: string) => void;
}

const ProfileSelect = ({ value, onChange }: Props) => {
    const { data } = useQuery(['clientEndpoint', 'list'], () => listClientProfiles(), {
        keepPreviousData: false,
        select: ({ data }) => data,
    });
    // TODO loading indicator & sorting

    return (
        <Select variant="standard" onChange={(ev) => onChange(ev.target.value)} value={value ?? ''}>
            {data &&
                data.map((profile) => (
                    <MenuItem key={profile.id} value={profile.id}>
                        {profile.name}
                    </MenuItem>
                ))}
        </Select>
    );
};

export default ProfileSelect;
