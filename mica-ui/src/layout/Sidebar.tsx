import ApiIcon from '@mui/icons-material/Api';
import CorporateFareIcon from '@mui/icons-material/CorporateFare';
import DashboardIcon from '@mui/icons-material/Dashboard';
import PublishIcon from '@mui/icons-material/Publish';
import { Collapse, List } from '@mui/material';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';

import { Link, useLocation } from 'react-router-dom';

const Sidebar = ({ open }: { open: boolean }) => {
    const location = useLocation();

    return (
        <List component="nav">
            {/*Dashboard*/}
            <ListItemButton component={Link} to="/" selected={location.pathname === '/'}>
                <ListItemIcon>
                    <DashboardIcon />
                </ListItemIcon>
                <ListItemText primary="Dashboard" />
            </ListItemButton>

            {/*API*/}
            <ListItemButton component={Link} to="/api" selected={location.pathname === '/api'}>
                <ListItemIcon>
                    <ApiIcon />
                </ListItemIcon>
                <ListItemText primary="API" />
            </ListItemButton>

            {/*Entities*/}
            <ListItemButton
                component={Link}
                to="/entity"
                selected={location.pathname === '/entity'}>
                <ListItemIcon>
                    <CorporateFareIcon />
                </ListItemIcon>
                <ListItemText primary="Entities" />
            </ListItemButton>

            {/*Entities/Details*/}
            {open && /^\/entity\/.*\/details$/.test(location.pathname) && (
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
