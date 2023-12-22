import { Link } from '@mui/material';

import { PropsWithChildren } from 'react';

interface Props {
    href: string;
}

const ExternalLink = ({ href, children }: PropsWithChildren<Props>) => {
    return (
        <Link href={href} target="_blank" rel="noopener noreferrer">
            {children}
        </Link>
    );
};

export default ExternalLink;
