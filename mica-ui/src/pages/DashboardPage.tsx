import { DashboardRenderResponse, render } from '../api/dashboard.ts';
import { ApiError } from '../api/error.ts';
import CopyToClipboardButton from '../components/CopyToClipboardButton.tsx';
import PageTitle from '../components/PageTitle.tsx';
import ReadableApiError from '../components/ReadableApiError.tsx';
import RenderDashboard from '../features/dashboard/RenderDashboard.tsx';
import EditIcon from '@mui/icons-material/Edit';
import LinkIcon from '@mui/icons-material/Link';
import {
    Backdrop,
    Button,
    CircularProgress,
    Container,
    FormControl,
    Grid,
    Stack,
    Typography,
} from '@mui/material';

import { useQuery } from '@tanstack/react-query';
import { useNavigate, useParams } from 'react-router-dom';

const CopyPermalinkButton = ({ entityId }: { entityId: string | undefined }) => {
    if (!entityId) {
        return <></>;
    }
    return (
        <Typography variant="h6">
            <CopyToClipboardButton
                text={`${window.location.protocol}//${window.location.host}/mica/dashboard/${entityId}`}
                tooltipText="Copy permalink"
                Icon={LinkIcon}
            />
        </Typography>
    );
};

const DashboardPage = () => {
    const navigate = useNavigate();

    const { entityId } = useParams();

    const { data, isLoading, error, isError } = useQuery<DashboardRenderResponse, ApiError>({
        queryKey: ['dashboard', entityId],
        queryFn: () => render(entityId!),
        enabled: entityId !== undefined,
        retry: false,
        refetchOnWindowFocus: false,
        refetchOnReconnect: false,
    });

    if (isError) {
        return <ReadableApiError error={error} />;
    }

    if (isLoading || !data) {
        return (
            <Backdrop open={true} sx={{ backgroundColor: '#fff' }}>
                <CircularProgress />
            </Backdrop>
        );
    }

    return (
        <Container sx={{ mt: 2 }} maxWidth="xl">
            <Grid container sx={{ mb: 1 }}>
                <Grid size={10}>
                    <PageTitle>{data.dashboard.title}</PageTitle>
                </Grid>
                <Grid size={2} display="flex" justifyContent="flex-end">
                    <Stack direction="row" spacing={2}>
                        <FormControl>
                            <CopyPermalinkButton entityId={entityId} />
                        </FormControl>
                        <FormControl>
                            <Button
                                startIcon={<EditIcon />}
                                variant="outlined"
                                onClick={() => navigate(`/entity/${entityId}/edit`)}>
                                Edit
                            </Button>
                        </FormControl>
                    </Stack>
                </Grid>
            </Grid>
            <RenderDashboard dashboard={data.dashboard} data={data.data} />
        </Container>
    );
};

export default DashboardPage;
