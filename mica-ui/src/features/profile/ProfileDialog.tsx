import { CurrentUser } from '../../UserContext.tsx';
import CopyToClipboardButton from '../../components/CopyToClipboardButton.tsx';
import AccountCircleIcon from '@mui/icons-material/AccountCircle';
import GroupIcon from '@mui/icons-material/Group';
import GroupOutlinedIcon from '@mui/icons-material/GroupOutlined';
import SecurityIcon from '@mui/icons-material/Security';
import {
    Box,
    Button,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    Divider,
    FormControl,
    FormControlLabel,
    FormLabel,
    List,
    ListItem,
    ListSubheader,
    Radio,
    RadioGroup,
    useColorScheme,
} from '@mui/material';
import ListItemIcon from '@mui/material/ListItemIcon';

import React from 'react';

interface Props {
    open: boolean;
    onClose: () => void;
    user: CurrentUser;
}

const ProfileDialog = ({ open, onClose, user }: Props) => {
    const { mode, setMode } = useColorScheme();

    const handleThemeChange = React.useCallback(
        (ev: React.ChangeEvent<HTMLInputElement>) => {
            const mode = ev.target.value as 'system' | 'light' | 'dark';
            setMode(mode);
        },
        [setMode],
    );

    return (
        <Dialog open={open} onClose={onClose} fullWidth={true}>
            <DialogTitle>
                <Box display="flex" alignItems="center">
                    <AccountCircleIcon sx={{ mr: 2 }} /> {user.username}
                </Box>
            </DialogTitle>
            <DialogContent>
                <FormControl>
                    <FormLabel>Theme</FormLabel>
                    <RadioGroup row={true} value={mode} onChange={handleThemeChange}>
                        <FormControlLabel value="system" control={<Radio />} label="System" />
                        <FormControlLabel value="light" control={<Radio />} label="Light" />
                        <FormControlLabel value="dark" control={<Radio />} label="Dark" />
                    </RadioGroup>
                </FormControl>
                <Divider sx={{ mt: 1, mb: 1 }} />
                <List>
                    <ListSubheader>User ID</ListSubheader>
                    {user.userId && (
                        <ListItem secondaryAction={<CopyToClipboardButton text={user.userId} />}>
                            {user.userId}
                        </ListItem>
                    )}
                    {!user.userId && <ListItem>n/a</ListItem>}
                </List>
                <List>
                    <ListSubheader>OIDC Groups</ListSubheader>
                    {user.oidcGroups &&
                        user.oidcGroups.length > 0 &&
                        user.oidcGroups.sort().map((group) => (
                            <ListItem key={group}>
                                <ListItemIcon>
                                    <GroupOutlinedIcon />
                                </ListItemIcon>
                                {group}
                            </ListItem>
                        ))}
                    {!user.oidcGroups ||
                        (user.oidcGroups.length === 0 && (
                            <ListItem>No OIDC groups assigned</ListItem>
                        ))}
                </List>
                <List>
                    <ListSubheader>Roles</ListSubheader>
                    {user.roles &&
                        user.roles.length > 0 &&
                        user.roles.sort().map((role) => (
                            <ListItem key={role}>
                                {' '}
                                <ListItemIcon>
                                    <SecurityIcon />
                                </ListItemIcon>
                                {role}
                            </ListItem>
                        ))}
                    {!user.roles ||
                        (user.roles.length === 0 && <ListItem>No roles assigned</ListItem>)}
                </List>
                <List>
                    <ListSubheader>Teams</ListSubheader>
                    {user.teams &&
                        user.teams.length > 0 &&
                        user.teams.map((team) => (
                            <ListItem key={team.orgName + '/' + team.teamName}>
                                <ListItemIcon>
                                    <GroupIcon />
                                </ListItemIcon>
                                {team.orgName} / {team.teamName} / {team.teamRole}
                            </ListItem>
                        ))}
                    {!user.teams ||
                        (user.teams.length === 0 && <ListItem>No teams assigned</ListItem>)}
                </List>
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose}>Close</Button>
            </DialogActions>
        </Dialog>
    );
};

export default ProfileDialog;
