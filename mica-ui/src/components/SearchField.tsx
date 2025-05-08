import ClearIcon from '@mui/icons-material/Clear';
import { IconButton, TextField } from '@mui/material';

interface Props {
    placeholder?: string;
    value: string;
    onChange: (value: string) => void;
}

const SearchField = ({ placeholder = 'Search', value, onChange }: Props) => {
    return (
        <TextField
            placeholder={placeholder}
            type="search"
            size="small"
            value={value}
            onChange={(ev) => onChange(ev.target.value)}
            slotProps={{
                input: {
                    sx: {
                        paddingRight: 1,
                    },
                    endAdornment: (
                        <IconButton size="small" onClick={() => onChange('')}>
                            <ClearIcon />
                        </IconButton>
                    ),
                },
            }}
        />
    );
};

export default SearchField;
