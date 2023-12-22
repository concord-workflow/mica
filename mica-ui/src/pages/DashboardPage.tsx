import ExternalLink from '../components/ExternalLink.tsx';
import SectionTitle from '../components/SectionTitle.tsx';
import GitHubIcon from '@mui/icons-material/GitHub';
import InfoIcon from '@mui/icons-material/Info';
import { Avatar, Container, List, ListItem, ListItemAvatar, Paper } from '@mui/material';

const DashboardPage = () => {
    return (
        <Container maxWidth="xl" sx={{ mt: 2 }}>
            <SectionTitle>Useful Links</SectionTitle>
            <Paper>
                <List>
                    <ListItem>
                        <ListItemAvatar>
                            <Avatar>
                                <GitHubIcon />
                            </Avatar>
                        </ListItemAvatar>
                        <ExternalLink href="https://github.com/concord-workflow/mica">
                            github.com/concord-workflow/mica
                        </ExternalLink>
                    </ListItem>
                    <ListItem>
                        <ListItemAvatar>
                            <Avatar>
                                <InfoIcon />
                            </Avatar>
                        </ListItemAvatar>
                        <ExternalLink href="https://github.com/concord-workflow/mica/tree/main/docs">
                            Documentation
                        </ExternalLink>
                    </ListItem>
                </List>
            </Paper>
        </Container>
    );
};

export default DashboardPage;
