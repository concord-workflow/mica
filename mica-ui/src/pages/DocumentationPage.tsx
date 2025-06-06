import IndexMd from '../../../docs/index.md?raw';
import { Box, Container, Link, Paper, useColorScheme, useTheme } from '@mui/material';
import rehypeAutolinkHeadings from 'rehype-autolink-headings';
import rehypeSlug from 'rehype-slug';

import React from 'react';
import Markdown from 'react-markdown';
import { Link as RouterLink, useLocation } from 'react-router-dom';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { materialDark, materialLight } from 'react-syntax-highlighter/dist/esm/styles/prism';

const StyledMarkdown = ({ content }: { content: string }) => {
    const theme = useTheme();

    const colorScheme = useColorScheme();
    const mode = colorScheme.mode === 'system' ? colorScheme.systemMode : colorScheme.mode;
    const markdownStyle = mode === 'light' ? materialLight : materialDark;

    const location = useLocation();
    const lastHash = React.useRef('');
    React.useEffect(() => {
        if (location.hash) {
            lastHash.current = location.hash.slice(1);
        }

        if (lastHash.current && document.getElementById(lastHash.current)) {
            setTimeout(() => {
                document
                    .getElementById(lastHash.current)
                    ?.scrollIntoView({ behavior: 'smooth', block: 'start' });
                lastHash.current = '';
            }, 100);
        }
    }, [location]);

    return (
        <Box
            component={'span'}
            sx={{
                // fix scrolling
                'h1[id], h2[id], h3[id], h4[id], h5[id], h6[id]': {
                    scrollMarginTop: '80px',
                },
                // typography
                '& h1': {
                    ...theme.typography.h3,
                    marginBottom: theme.spacing(2),
                    marginTop: theme.spacing(3),
                },
                '& h2': {
                    ...theme.typography.h4,
                    marginBottom: theme.spacing(1.5),
                    marginTop: theme.spacing(2.5),
                },
                '& h3': {
                    ...theme.typography.h5,
                    marginBottom: theme.spacing(1),
                    marginTop: theme.spacing(2),
                },
                '& p': {
                    ...theme.typography.body1,
                    marginBottom: theme.spacing(1),
                },
                '& blockquote': {
                    borderLeft: `4px solid ${theme.palette.primary.main}`,
                    paddingLeft: theme.spacing(2),
                    margin: theme.spacing(2, 0),
                    fontStyle: 'italic',
                    backgroundColor: theme.palette.action.hover,
                    padding: theme.spacing(1, 2),
                },
                '& code': {
                    backgroundColor: theme.palette.action.selected,
                    padding: theme.spacing(0.5, 1),
                    borderRadius: theme.shape.borderRadius,
                    fontFamily: 'monospace',
                    fontSize: '0.875em',
                },
                '& pre': {
                    backgroundColor: theme.palette.background.default,
                    border: `1px solid ${theme.palette.divider}`,
                    borderRadius: theme.shape.borderRadius,
                    padding: theme.spacing(2),
                    overflow: 'auto',
                    '& code': {
                        backgroundColor: 'transparent',
                        padding: 0,
                    },
                },
                '& ul, & ol': {
                    paddingLeft: theme.spacing(3),
                    marginBottom: theme.spacing(1),
                },
                '& li': {
                    marginBottom: theme.spacing(0.5),
                },
                '& a': {
                    color: theme.palette.primary.main,
                    textDecoration: 'none',
                    '&:hover': {
                        textDecoration: 'underline',
                    },
                },
                '& table': {
                    borderCollapse: 'collapse',
                    width: '100%',
                    marginBottom: theme.spacing(2),
                },
                '& th, & td': {
                    border: `1px solid ${theme.palette.divider}`,
                    padding: theme.spacing(1),
                    textAlign: 'left',
                },
                '& th': {
                    backgroundColor: theme.palette.action.hover,
                    fontWeight: theme.typography.fontWeightMedium,
                },
            }}>
            <Markdown
                rehypePlugins={[rehypeSlug, [rehypeAutolinkHeadings, { behavior: 'wrap' }]]}
                components={{
                    a(props) {
                        const { children, node: _node, ref: _ref, ...rest } = props;
                        return (
                            <Link component={RouterLink} to={props.href!} {...rest}>
                                {children}
                            </Link>
                        );
                    },
                    code(props) {
                        const { children, className, node: _node, ref: _ref, ...rest } = props;
                        const match = /language-(\w+)/.exec(className || '');
                        return match ? (
                            <SyntaxHighlighter
                                {...rest}
                                PreTag="div"
                                children={String(children).replace(/\n$/, '')}
                                language={match[1]}
                                style={markdownStyle}
                            />
                        ) : (
                            <code {...rest} className={className}>
                                {children}
                            </code>
                        );
                    },
                }}>
                {content}
            </Markdown>
        </Box>
    );
};

const DocumentationPage = () => {
    return (
        <Container sx={{ mt: 2, mb: 2 }} maxWidth="xl" component={Paper}>
            <StyledMarkdown content={IndexMd} />
        </Container>
    );
};

export default DocumentationPage;
