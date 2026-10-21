#define _GNU_SOURCE
#include <dlfcn.h>
#include <errno.h>
#include <limits.h>
#include <stddef.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

#ifndef PATH_MAX
#define PATH_MAX 4096
#endif

static int  (*real_rmdir)(const char *) = NULL;
static int  (*real_unlink)(const char *) = NULL;
static int  (*real_rmdirat)(int, const char *) = NULL;
static int  (*real_unlinkat)(int, const char *, int) = NULL;
static int  (*real_symlink)(const char *, const char *) = NULL;
static int  (*real_symlinkat)(const char *, int, const char *) = NULL;
static char *(*real_realpath)(const char *, char *) = NULL;

__attribute__((constructor))
static void vmfix_init(void)
{
    real_rmdir     = dlsym(RTLD_NEXT, "rmdir");
    real_unlink    = dlsym(RTLD_NEXT, "unlink");
    real_rmdirat   = dlsym(RTLD_NEXT, "rmdirat");
    real_unlinkat  = dlsym(RTLD_NEXT, "unlinkat");
    real_symlink   = dlsym(RTLD_NEXT, "symlink");
    real_symlinkat = dlsym(RTLD_NEXT, "symlinkat");
    real_realpath  = dlsym(RTLD_NEXT, "realpath");
}

static int is_dpkg_temp(const char *path)
{
    const char *base;
    size_t len;

    if (path == NULL)
        return 0;

    base = strrchr(path, '/');
    base = (base != NULL) ? base + 1 : path;

    if (base[0] == '\0')
        return 0;

    len = strlen(base);
    if (len > 9 && strcmp(base + len - 9, ".dpkg-tmp") == 0)
        return 1;
    if (len > 9 && strcmp(base + len - 9, ".dpkg-new") == 0)
        return 1;
    if (len > 9 && strcmp(base + len - 9, ".dpkg-old") == 0)
        return 1;

    return 0;
}

static int remap_errno(const char *path, int rc)
{
    if (rc != 0 && errno == EACCES && is_dpkg_temp(path))
        errno = ENOENT;
    return rc;
}

int rmdir(const char *path)
{
    int rc;
    if (real_rmdir == NULL) { errno = EACCES; return -1; }
    rc = real_rmdir(path);
    return remap_errno(path, rc);
}

int unlink(const char *path)
{
    int rc;
    if (real_unlink == NULL) { errno = EACCES; return -1; }
    rc = real_unlink(path);
    return remap_errno(path, rc);
}

int rmdirat(int dirfd, const char *path)
{
    int rc;
    if (real_rmdirat == NULL) { errno = ENOSYS; return -1; }
    rc = real_rmdirat(dirfd, path);
    return remap_errno(path, rc);
}

int unlinkat(int dirfd, const char *path, int flags)
{
    int rc;
    if (real_unlinkat == NULL) { errno = ENOSYS; return -1; }
    rc = real_unlinkat(dirfd, path, flags);
    return remap_errno(path, rc);
}

static int make_absolute_target(const char *target, const char *linkpath,
                                char *out, size_t outsz)
{
    char dir[PATH_MAX];
    char joined[PATH_MAX];
    const char *slash;
    size_t dirlen;
    size_t i, o;

    if (target == NULL || linkpath == NULL || out == NULL || outsz == 0)
        return 0;

    if (target[0] == '/') {
        if (strlen(target) + 1 > outsz)
            return 0;
        strcpy(out, target);
        return 1;
    }

    if (target[0] == '\0')
        return 0;

    slash = strrchr(linkpath, '/');
    if (slash == NULL) {

        dirlen = 1;
        dir[0] = '.';
        dir[1] = '\0';
    } else if (slash == linkpath) {

        dirlen = 1;
        dir[0] = '/';
        dir[1] = '\0';
    } else {
        dirlen = (size_t)(slash - linkpath);
        if (dirlen >= sizeof(dir))
            return 0;
        memcpy(dir, linkpath, dirlen);
        dir[dirlen] = '\0';
    }

    if (dir[0] != '/') {
        char cwd[PATH_MAX];
        char rel[PATH_MAX];

        if (getcwd(cwd, sizeof(cwd)) == NULL)
            return 0;

        if (strlen(dir) + 1 > sizeof(rel))
            return 0;
        strcpy(rel, dir);

        if (strlen(cwd) + 1 + strlen(rel) + 1 > sizeof(dir))
            return 0;

        if (strcmp(rel, ".") == 0) {
            strcpy(dir, cwd);
        } else {
            strcpy(dir, cwd);
            if (dir[strlen(dir) - 1] != '/')
                strcat(dir, "/");
            strcat(dir, rel);
        }
        dirlen = strlen(dir);
    }

    if (dirlen + 1 + strlen(target) + 1 > sizeof(joined))
        return 0;
    strcpy(joined, dir);
    if (dirlen > 0 && dir[dirlen - 1] != '/')
        strcat(joined, "/");
    strcat(joined, target);

    {
        char *comps[256];
        int ncomp = 0;
        char *p = joined;
        int absolute = (joined[0] == '/');

        while (*p != '\0') {
            char *start = p;
            while (*p != '\0' && *p != '/')
                p++;
            if (*p == '/')
                *p++ = '\0';

            if (start[0] == '\0' || strcmp(start, ".") == 0)
                continue;
            if (strcmp(start, "..") == 0) {
                if (ncomp > 0 && strcmp(comps[ncomp - 1], "..") != 0)
                    ncomp--;
                else if (!absolute && ncomp < 256)
                    comps[ncomp++] = start;
                continue;
            }
            if (ncomp < 256)
                comps[ncomp++] = start;
        }

        o = 0;
        if (absolute) {
            if (o + 1 >= outsz)
                return 0;
            out[o++] = '/';
        }
        for (i = 0; i < (size_t)ncomp; i++) {
            size_t clen = strlen(comps[i]);
            if (o + clen + 2 >= outsz)
                return 0;
            if (o > 0 && !(o == 1 && out[0] == '/'))
                out[o++] = '/';
            memcpy(out + o, comps[i], clen);
            o += clen;
        }
        out[o] = '\0';
    }

    return (out[0] != '\0');
}

int symlink(const char *target, const char *linkpath)
{
    char abs[PATH_MAX];

    if (real_symlink == NULL) { errno = ENOSYS; return -1; }

    if (make_absolute_target(target, linkpath, abs, sizeof(abs)))
        return real_symlink(abs, linkpath);

    return real_symlink(target, linkpath);
}

int symlinkat(const char *target, int newdirfd, const char *linkpath)
{
    char abs[PATH_MAX];

    if (real_symlinkat == NULL) { errno = ENOSYS; return -1; }

    if (make_absolute_target(target, linkpath, abs, sizeof(abs)))
        return real_symlinkat(abs, newdirfd, linkpath);

    return real_symlinkat(target, newdirfd, linkpath);
}
