import CorporateFareIcon from '@mui/icons-material/CorporateFare';
import DashboardIcon from '@mui/icons-material/Dashboard';
import PublishIcon from '@mui/icons-material/Publish';
import SourceIcon from '@mui/icons-material/Source';
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
            {/*Dashboard*/}
            <ListItemButton onClick={() => navigate('/')} selected={location.pathname === '/'}>
                <ListItemIcon>
                    <DashboardIcon />
                </ListItemIcon>
                <ListItemText primary="Dashboard" />
            </ListItemButton>

            {/*Clients*/}
            <ListItemButton
                onClick={() => navigate('/client')}
                selected={location.pathname === '/client'}>
                <ListItemIcon>
                    <CorporateFareIcon />
                </ListItemIcon>
                <ListItemText primary="Clients" />
            </ListItemButton>

            {/*Clients/Details*/}
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

            {/*Profiles*/}
            <ListItemButton
                onClick={() => navigate('/profile')}
                selected={location.pathname === '/profile'}>
                <ListItemIcon>
                    <SourceIcon />
                </ListItemIcon>
                <ListItemText primary="Profiles" />
            </ListItemButton>
        </List>
    );
};

export default Sidebar;
