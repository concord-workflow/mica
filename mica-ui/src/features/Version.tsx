import { getSystemInfo } from '../api/system.ts';

import React, { useEffect } from 'react';

const Version = () => {
    const [version, setVersion] = React.useState<string | undefined>();

    useEffect(() => {
        const fetchVersion = async () => {
            const response = await getSystemInfo();
            setVersion(response.version);
        };
        fetchVersion();
    }, []);

    if (!version) {
        return null;
    }

    return <>{version}</>;
};

export default Version;
