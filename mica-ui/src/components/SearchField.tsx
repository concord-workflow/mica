import { TextField, debounce } from '@mui/material';

import { useMemo } from 'react';

interface Props {
    onChange: (value: string) => void;
}

const SearchField = ({ onChange }: Props) => {
    const debouncedOnChange = useMemo(() => debounce(onChange, 100), [onChange]);
    return (
        <TextField
            placeholder="Search"
            type="search"
            size="small"
            onChange={(ev) => debouncedOnChange(ev.target.value)}
        />
    );
};

export default SearchField;
