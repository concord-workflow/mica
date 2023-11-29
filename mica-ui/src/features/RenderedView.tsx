import { render } from '../api/view.ts';
import { CircularProgress } from '@mui/material';

import { useQuery } from 'react-query';

interface Props {
    viewId: string;
}

const RenderedView = ({ viewId }: Props) => {
    const { data, isFetching } = useQuery(['view', viewId, 'render'], () => render(viewId), {
        keepPreviousData: false,
    });

    if (isFetching) {
        return <CircularProgress />;
    }

    return <pre>{JSON.stringify(data, null, 2)}</pre>;
};

export default RenderedView;
