import { ObjectSchemaNode } from '../../api/schema.ts';
import { Box, TextField, Typography } from '@mui/material';

import React from 'react';

const StringField = ({
    name,
    value,
    onChange,
}: {
    name: string;
    value: string;
    onChange: (name: string, value: string) => void;
}) => {
    const handleOnChange = React.useCallback(
        (ev: React.ChangeEvent<HTMLInputElement>) => {
            onChange(name, ev.target.value);
        },
        [name, onChange],
    );

    return (
        <TextField
            fullWidth={true}
            label={name}
            size="small"
            value={value}
            onChange={handleOnChange}
        />
    );
};

const ParameterField = ({
    name,
    property,
    values,
    onChange,
}: {
    name: string;
    property: ObjectSchemaNode | undefined;
    values: Record<string, string | null>;
    onChange: (name: string, value: string) => void;
}) => {
    if (!property) {
        return <Typography>Unknown parameter: {name}</Typography>;
    }

    switch (property.type) {
        case 'string':
            return <StringField name={name} value={values[name] ?? ''} onChange={onChange} />;
        default:
            return <Typography>Unknown type: {property.type}</Typography>;
    }
};

const ViewParameters = ({
    parameters,
    values,
    onChange,
}: {
    parameters?: ObjectSchemaNode;
    values: Record<string, string | null>;
    onChange: (name: string, value: string) => void;
}) => {
    const fields = parameters ? Object.keys(parameters.properties ?? {}) : [];
    return (
        <Box textAlign="right" marginRight={2}>
            <Typography variant="h6" marginRight={2}>
                Parameters
            </Typography>
            {fields.length === 0 && <Typography variant="caption">n/a</Typography>}
            {fields.length > 0 &&
                fields.map((name) => (
                    <Box key={name} margin={2}>
                        <ParameterField
                            name={name}
                            property={parameters?.properties?.[name]}
                            values={values}
                            onChange={onChange}
                        />
                    </Box>
                ))}
        </Box>
    );
};

export default ViewParameters;
