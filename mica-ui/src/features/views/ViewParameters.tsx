import { JsonNode, ObjectSchemaNode } from '../../api/schema.ts';
import {
    Box,
    FormControl,
    InputLabel,
    MenuItem,
    Select,
    SelectChangeEvent,
    TextField,
    Typography,
} from '@mui/material';

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

interface EnumStringFieldProps extends StringFieldProps {
    enumValues: Array<string>;
}

const EnumStringField = ({ name, value, onChange, required, enumValues }: EnumStringFieldProps) => {
    const handleOnChange = React.useCallback(
        (ev: SelectChangeEvent) => {
            onChange(name, ev.target.value);
        },
        [name, onChange],
    );

    return (
        <FormControl size="small" fullWidth={true} required={required}>
            <InputLabel
                id={`${name}-select-label`}
                sx={(theme) => ({ bgcolor: theme.palette.background.default })}>
                {name}
            </InputLabel>
            <Select
                labelId={`${name}-select-label`}
                name={name}
                value={value}
                onChange={handleOnChange}>
                <MenuItem value={''}>-</MenuItem>
                {enumToStrings(enumValues).map((v) => (
                    <MenuItem key={v} value={v}>
                        {v}
                    </MenuItem>
                ))}
            </Select>
        </FormControl>
    );
};

const toString = (v: JsonNode): string | null => (v === null ? null : v.toString());
const onlyUnique = (v: string, idx: number, arr: Array<string>): boolean => arr.indexOf(v) === idx;
const enumToStrings = (v: Array<JsonNode>): Array<string> =>
    v
        .map(toString)
        .filter((v) => v !== null)
        .filter(onlyUnique);

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
            if (property.enum && property.enum.length > 0) {
                return (
                    <EnumStringField
                        name={name}
                        value={values[name] ?? ''}
                        required={required}
                        onChange={onChange}
                        enumValues={enumToStrings(property.enum)}
                    />
                );
            }

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
