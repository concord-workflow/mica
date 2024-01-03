export type JsonNode =
    | { [key: string]: JsonNode }
    | Array<JsonNode>
    | string
    | number
    | boolean
    | null;

// should be synced with ca.ibodrov.mica.schema.ObjectSchemaNode
export interface ObjectSchemaNode {
    type?: string;
    properties?: Record<string, ObjectSchemaNode>;
    required?: string[];
    enum?: JsonNode[];
    items?: ObjectSchemaNode;
    additionalProperties?: JsonNode;
    $ref?: string;
}
