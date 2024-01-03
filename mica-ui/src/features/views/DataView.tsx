import { Box } from '@mui/material';

import JsonView from 'react18-json-view';
import 'react18-json-view/src/style.css';

interface Props {
    data: unknown;
}

const DataView = ({ data }: Props) => {
    return (
        <Box margin={1} fontSize="large">
            <JsonView src={data} />
        </Box>
    );
};

export default DataView;
