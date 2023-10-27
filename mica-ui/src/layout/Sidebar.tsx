import CorporateFareIcon from '@mui/icons-material/CorporateFare';
import DashboardIcon from '@mui/icons-material/Dashboard';
import PublishIcon from '@mui/icons-material/Publish';
import { Collapse, List } from '@mui/material';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';

import { useLocation, useNavigate } from 'react-router-dom';

const Sidebar = ({ open }: { open: boolean }) => {
    const navigate = useNavigate();
    const location = useLocation();

    return (
        <List component="nav">
            <ListItemButton onClick={() => navigate('/')} selected={location.pathname === '/'}>
                <ListItemIcon>
                    <DashboardIcon />
                </ListItemIcon>
                <ListItemText primary="Dashboard" />
            </ListItemButton>
            <ListItemButton
                onClick={() => navigate('/client')}
                selected={location.pathname === '/client'}>
                <ListItemIcon>
                    <CorporateFareIcon />
                </ListItemIcon>
                <ListItemText primary="Clients" />
            </ListItemButton>
            {open && /^\/client\/.*\/details$/.test(location.pathname) && (
                <Collapse in={true} timeout="auto" unmountOnExit>
                    <List component="div" disablePadding>
                        <ListItemButton sx={{ pl: 4 }} selected={true}>
                            <ListItemIcon>
                                <PublishIcon />
                            </ListItemIcon>
                            <ListItemText primary="Details" />
                        </ListItemButton>
                    </List>
                </Collapse>
            )}
        </List>
    );
};

export default Sidebar;
