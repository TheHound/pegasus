/**
 * 
 */
package org.brekka.pegasus.web.filter;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.brekka.pegasus.core.model.AllocationFile;
import org.brekka.pegasus.core.model.AnonymousTransfer;
import org.brekka.pegasus.core.model.PegasusTokenType;
import org.brekka.pegasus.core.model.Token;
import org.brekka.pegasus.core.model.TokenType;
import org.brekka.pegasus.core.services.AnonymousTransferService;
import org.brekka.pegasus.core.services.DownloadService;
import org.brekka.pegasus.core.services.TokenService;
import org.brekka.pegasus.web.pages.deposit.MakeDeposit;
import org.brekka.pegasus.web.pages.direct.UnlockDirect;
import org.brekka.xml.pegasus.v2.model.FileType;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

/**
 * A filter that identifies paths containing tokens. A token must start with a number or upper case character. 
 * Subsequent characters may be mixed case, numbers or underscore.
 * 
 * @author Andrew Taylor (andrew@brekka.org)
 */
public class TokenFilter implements Filter {
    
    private static final Pattern TOKEN_PATTERN = Pattern.compile(String.format(
            "^(?:/([0-9]+))?/([A-Z0-9][A-Za-z0-9_]{%d,%d})(?:\\.zip|/[^/]+)?$",
            PegasusTokenType.MIN_LENGTH, PegasusTokenType.MAX_LENGTH));
    
    private AnonymousTransferService anonymousService;
    
    private DownloadService downloadService;
    
    private TokenService tokenService;

    /* (non-Javadoc)
     * @see javax.servlet.Filter#init(javax.servlet.FilterConfig)
     */
    @Override
    public void init(final FilterConfig filterConfig) throws ServletException {
        Thread startup = new Thread(new Runnable() {
            @Override
            public void run() {
                synchronized (TokenFilter.this) {
                    WebApplicationContext applicationContext = null;
                    while (applicationContext == null) {
                        applicationContext = WebApplicationContextUtils.getWebApplicationContext(filterConfig.getServletContext());
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                        }
                    }
                    downloadService = applicationContext.getBean(DownloadService.class);
                    anonymousService = applicationContext.getBean(AnonymousTransferService.class);
                    tokenService = applicationContext.getBean(TokenService.class);
                }
            }
        });
        startup.setDaemon(true);
        startup.start();
    }

    /* (non-Javadoc)
     * @see javax.servlet.Filter#doFilter(javax.servlet.ServletRequest, javax.servlet.ServletResponse, javax.servlet.FilterChain)
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException,
            ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        
        String requestURI = req.getRequestURI();
        requestURI = requestURI.substring(req.getContextPath().length());
        Matcher matcher = TOKEN_PATTERN.matcher(requestURI);
        if (matcher.matches()) {
            String code = matcher.group(1);
            String token = matcher.group(2);
            if (code == null) {
                Token tokenObj = tokenService.retrieveByPath(token);
                if (tokenObj == null) {
                    resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                } else {
                    TokenType tokenType = tokenObj.getType();
                    if (tokenType instanceof PegasusTokenType) {
                        PegasusTokenType ptt = (PegasusTokenType) tokenType;
                        switch (ptt) {
                            case ANON:
                                resp.sendRedirect(String.format("%s/%s/%s", 
                                        req.getContextPath(), UnlockDirect.PATH, token));
                                break;
                            case INBOX:
                                resp.sendRedirect(String.format("%s/%s/%s", 
                                        req.getContextPath(), MakeDeposit.PATH, token));
                                break;
                            default:
                                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                                break;
                        }
                    }
                }
                // Redirect token to unlock.
                return;
            }
            dispatchBundle(token, code, resp);
        } else {
            chain.doFilter(request, response);
        }

    }

    /**
     * @param token
     * @param code
     * @param resp
     */
    private void dispatchBundle(String token, String code, HttpServletResponse resp) throws ServletException, IOException {
        AnonymousTransfer anonymousTransfer = anonymousService.unlock(token, code);
        List<AllocationFile> fileList = anonymousTransfer.getFiles();
        if (fileList.size() == 1) {
            // Just one file, return it.
            AllocationFile file = fileList.get(0);
            FileType fileType = file.getXml();
            resp.setHeader("Content-Length", String.valueOf(fileType.getLength()));
            resp.setHeader("Content-Disposition", "attachment; filename=\"" + fileType.getName() + "\"");
            resp.setContentType(fileType.getMimeType());
            try ( InputStream is = downloadService.download(file, null) ) {
                IOUtils.copy(is, resp.getOutputStream());
            }
        } else {
            // Return a zip archive
            resp.setContentType("application/zip");
            resp.setHeader("Content-Disposition", "attachment; filename=\"Bundle_" + token + ".zip\"");
            ZipOutputStream zos = new ZipOutputStream(resp.getOutputStream());
            zos.setLevel(ZipEntry.STORED);
            for (AllocationFile file : fileList) {
                FileType fileType = file.getXml();
                ZipEntry ze = new ZipEntry(fileType.getName());
                ze.setSize(fileType.getLength());
                zos.putNextEntry(ze);
                try ( InputStream is = downloadService.download(file, null) ) {
                    IOUtils.copy(is, zos);
                }
                zos.closeEntry();
            }
            zos.finish();
        }
    }

    /* (non-Javadoc)
     * @see javax.servlet.Filter#destroy()
     */
    @Override
    public void destroy() {
        // Not needed
    }

}
