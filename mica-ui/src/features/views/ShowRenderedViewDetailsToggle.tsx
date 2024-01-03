import { FormControl, FormControlLabel, Switch, Tooltip } from '@mui/material';

import React from 'react';

interface Props {
    checked: boolean;
    onChange: (checked: boolean) => void;
}

const ShowRenderedViewDetailsToggle = ({ checked, onChange }: Props) => {
    const handleOnChange = React.useCallback(
        (ev: React.ChangeEvent<HTMLInputElement>) => onChange(ev.target.checked),
        [onChange],
    );

    const Toggle = React.useMemo(
        () => <Switch checked={checked} onChange={handleOnChange} />,
        [checked, handleOnChange],
    );

    return (
        <FormControl>
            <Tooltip title={'When enabled, show the result as is, including system properties.'}>
                <FormControlLabel control={Toggle} label="Details" />
            </Tooltip>
        </FormControl>
    );
};

export default ShowRenderedViewDetailsToggle;
