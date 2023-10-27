import ActionBar from '../components/ActionBar.tsx';
import PageTitle from '../components/PageTitle.tsx';
import RowActionMenuButton from '../components/RowMenu.tsx';
import SearchField from '../components/SearchField.tsx';
import Spacer from '../components/Spacer.tsx';
import AddIcon from '@mui/icons-material/Add';
import CheckCircleOutlineTwoToneIcon from '@mui/icons-material/CheckCircleOutlineTwoTone';
import {
    Button,
    FormControl,
    Paper,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
} from '@mui/material';

const DeploymentListPage = () => {
    return (
        <>
            <PageTitle>Deployments</PageTitle>
            <ActionBar>
                <FormControl>
                    <Button startIcon={<AddIcon />} variant="contained">
                        Start new
                    </Button>
                </FormControl>
                <Spacer />
                <SearchField onChange={() => {}} />
            </ActionBar>
            <TableContainer component={Paper}>
                <Table>
                    <TableHead>
                        <TableRow>
                            <TableCell>Status</TableCell>
                            <TableCell>ID</TableCell>
                            <TableCell>Type</TableCell>
                            <TableCell>Duration</TableCell>
                            <TableCell width="1%"></TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        <TableRow>
                            <TableCell>
                                <CheckCircleOutlineTwoToneIcon />
                            </TableCell>
                            <TableCell>a05fa9f1-8feb-4f01-b80d-89ee05636882</TableCell>
                            <TableCell>Concord Flow</TableCell>
                            <TableCell>7m 53s</TableCell>
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

export default DeploymentListPage;
