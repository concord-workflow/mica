import { getProfile } from '../api/profile.ts';
import PageTitle from '../components/PageTitle.tsx';
import { CircularProgress, Container, Paper, Typography } from '@mui/material';

import React from 'react';
import { useQuery } from 'react-query';
import { useParams } from 'react-router-dom';

const HELP: React.ReactNode = (
    <>
        <b>Profile Details</b> page provides overview of all client-related data in Mica.
    </>
);

type RouteParams = {
    profileName: string;
};

const ProfileDetailsPage = () => {
    const { profileName } = useParams<RouteParams>();
    const { data, isFetching } = useQuery(
        ['profile', profileName],
        () => getProfile(profileName!),
        {
            enabled: profileName !== undefined,
        },
    );

    return (
        <>
            <PageTitle help={HELP}>Profile</PageTitle>
            <Container maxWidth="lg">
                <Typography variant="h5" sx={{ marginBottom: 1 }}>
                    Metadata {isFetching && <CircularProgress size={16} />}
                </Typography>
                {data && (
                    <Paper sx={{ padding: 2 }}>
                        <pre>{JSON.stringify(data.schema, null, 2)}</pre>
                    </Paper>
                )}
            </Container>
        </>
    );
};

export default ProfileDetailsPage;
