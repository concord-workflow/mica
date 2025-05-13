import { CurrentUser } from '../UserContext.tsx';
import CopyToClipboardButton from '../components/CopyToClipboardButton.tsx';
import GroupIcon from '@mui/icons-material/Group';
import {
    Button,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    List,
    ListItem,
    ListSubheader,
} from '@mui/material';
import ListItemIcon from '@mui/material/ListItemIcon';

interface Props {
    open: boolean;
    onClose: () => void;
    user: CurrentUser;
}

const ProfileDialog = ({ open, onClose, user }: Props) => {
    return (
        <Dialog open={open} onClose={onClose} fullWidth={true}>
            <DialogTitle>{user.username}</DialogTitle>
            <DialogContent>
                <List>
                    <ListSubheader>ID</ListSubheader>
                    {user.userId && (
                        <ListItem secondaryAction={<CopyToClipboardButton text={user.userId} />}>
                            {user.userId}
                        </ListItem>
                    )}
                    {!user.userId && <ListItem>n/a</ListItem>}
                </List>
                <List>
                    <ListSubheader>Roles</ListSubheader>
                    {user.roles &&
                        user.roles.length > 0 &&
                        user.roles.sort().map((role) => <ListItem key={role}>{role}</ListItem>)}
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
