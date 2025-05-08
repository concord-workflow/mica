import { ObjectSchemaNode } from '../../api/schema.ts';
import { Box, TextField, Typography } from '@mui/material';

import React from 'react';

interface StringFieldProps {
    name: string;
    value: string;
    onChange: (name: string, value: string) => void;
    required: boolean | undefined;
}

const StringField = ({ name, value, onChange, required }: StringFieldProps) => {
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
            required={required}
            onChange={handleOnChange}
        />
    );
};

interface ParameterFieldProps {
    name: string;
    property: ObjectSchemaNode | undefined;
    required: boolean | undefined;
    values: Record<string, string | null>;
    onChange: (name: string, value: string) => void;
}

const ParameterField = ({ name, property, required, values, onChange }: ParameterFieldProps) => {
    if (!property) {
        return <Typography>Unknown parameter: {name}</Typography>;
    }

    switch (property.type) {
        case 'string':
            return (
                <StringField
                    name={name}
                    value={values[name] ?? ''}
                    required={required}
                    onChange={onChange}
                />
            );
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
    const fields = parameters ? Object.keys(parameters.properties ?? {}).sort() : [];
    return (
        <>
            <Typography variant="h6" marginRight={2}>
                Parameters
            </Typography>
            {fields.length === 0 && (
                <Typography variant="caption">
                    No <code>parameters</code> defined in the view.
                </Typography>
            )}
            {fields.length > 0 &&
                fields.map((name) => (
                    <Box key={name} margin={2}>
                        <ParameterField
                            name={name}
                            property={parameters?.properties?.[name]}
                            required={parameters?.required?.includes(name)}
                            values={values}
                            onChange={onChange}
                        />
                    </Box>
                ))}
        </>
    );
};

export default ViewParameters;
