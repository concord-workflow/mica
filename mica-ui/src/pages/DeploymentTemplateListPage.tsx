import ActionBar from '../components/ActionBar.tsx';
import PageTitle from '../components/PageTitle.tsx';
import RowActionMenuButton from '../components/RowMenu.tsx';
import SearchField from '../components/SearchField.tsx';
import Spacer from '../components/Spacer.tsx';
import AddIcon from '@mui/icons-material/Add';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import {
    Button,
    FormControl,
    IconButton,
    Paper,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Tooltip,
} from '@mui/material';

import { useNavigate } from 'react-router-dom';

const StartDeploymentButton = () => {
    const navigate = useNavigate();

    return (
        <Tooltip title="Start a new deployment using this template">
            <IconButton size="small" onClick={() => navigate('/deployment')}>
                <PlayArrowIcon />
            </IconButton>
        </Tooltip>
    );
};

const DeploymentTemplateListPage = () => {
    return (
        <>
            <PageTitle>Deployment Templates</PageTitle>
            <ActionBar>
                <FormControl>
                    <Button startIcon={<AddIcon />} variant="contained">
                        Add
                    </Button>
                </FormControl>
                <Spacer />
                <SearchField onChange={() => {}} />
            </ActionBar>
            <TableContainer component={Paper}>
                <Table>
                    <TableHead>
                        <TableRow>
                            <TableCell width="1%"></TableCell>
                            <TableCell>Name</TableCell>
                            <TableCell>Type</TableCell>
                            <TableCell>Description</TableCell>
                            <TableCell width="1%"></TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        <TableRow>
                            <TableCell>
                                <StartDeploymentButton />
                            </TableCell>
                            <TableCell>bootstrap-cluster-using-ck8s</TableCell>
                            <TableCell>Concord Flow</TableCell>
                            <TableCell>Bootstrap a ck8s cluster using ck8s-cli</TableCell>
                            <TableCell align="right">
                                <RowActionMenuButton />
                            </TableCell>
                        </TableRow>
                    </TableBody>
                </Table>
            </TableContainer>
        </>
    );
};

export default DeploymentTemplateListPage;
