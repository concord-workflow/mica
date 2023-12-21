import { FormControl, FormControlLabel, Switch, Tooltip } from '@mui/material';

interface Props {
    checked: boolean;
    onChange: (checked: boolean) => void;
}

const ShowRenderedViewDetailsToggle = ({ checked, onChange }: Props) => {
    return (
        <FormControl>
            <Tooltip title={'When enabled, show the result as is, including system properties.'}>
                <FormControlLabel
                    control={
                        <Switch checked={checked} onChange={(ev) => onChange(ev.target.checked)} />
                    }
                    label="Details"
                />
            </Tooltip>
        </FormControl>
    );
};

export default ShowRenderedViewDetailsToggle;
