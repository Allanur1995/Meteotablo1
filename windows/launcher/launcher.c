#ifndef UNICODE
#define UNICODE
#endif
#ifndef _UNICODE
#define _UNICODE
#endif
#define WIN32_LEAN_AND_MEAN

#include <windows.h>
#include <wchar.h>

#define PATH_BUF_LEN 2048
#define CMD_BUF_LEN  4608

static int file_exists(const wchar_t *path) {
    DWORD attrs = GetFileAttributesW(path);
    return (attrs != INVALID_FILE_ATTRIBUTES) && ((attrs & FILE_ATTRIBUTE_DIRECTORY) == 0);
}

static int join_path(wchar_t *out, size_t cap, const wchar_t *base, const wchar_t *rel) {
    size_t base_len = wcslen(base);
    size_t rel_len = wcslen(rel);
    int need_sep = (base_len > 0 && base[base_len - 1] != L'\\' && base[base_len - 1] != L'/') ? 1 : 0;
    if (base_len + (size_t)need_sep + rel_len + 1 > cap) {
        return 0;
    }
    wmemcpy(out, base, base_len);
    size_t pos = base_len;
    if (need_sep) {
        out[pos++] = L'\\';
    }
    wmemcpy(out + pos, rel, rel_len);
    out[pos + rel_len] = L'\0';
    return 1;
}

static int get_exe_dir(wchar_t *dir_out, size_t cap) {
    DWORD len = GetModuleFileNameW(NULL, dir_out, (DWORD)cap);
    if (len == 0 || len >= (DWORD)cap) {
        return 0;
    }
    for (DWORD i = len; i > 0; --i) {
        wchar_t ch = dir_out[i - 1];
        if (ch == L'\\' || ch == L'/') {
            dir_out[i - 1] = L'\0';
            return 1;
        }
    }
    return 0;
}

static int find_java_exe(const wchar_t *exe_dir, wchar_t *java_out, size_t cap) {
    wchar_t java_home[PATH_BUF_LEN];
    if (join_path(java_out, cap, exe_dir, L"jre\\bin\\javaw.exe") && file_exists(java_out)) {
        return 1;
    }
    DWORD env_len = GetEnvironmentVariableW(L"JAVA_HOME", java_home, PATH_BUF_LEN);
    if (env_len > 0 && env_len < PATH_BUF_LEN) {
        if (join_path(java_out, cap, java_home, L"bin\\javaw.exe") && file_exists(java_out)) {
            return 1;
        }
    }
    if (SearchPathW(NULL, L"javaw.exe", NULL, (DWORD)cap, java_out, NULL) > 0) {
        return 1;
    }
    return 0;
}

static int append_str(wchar_t *dst, size_t cap, size_t *pos, const wchar_t *src) {
    size_t len = wcslen(src);
    if (*pos + len + 1 > cap) {
        return 0;
    }
    wmemcpy(dst + *pos, src, len);
    *pos += len;
    dst[*pos] = L'\0';
    return 1;
}

int WINAPI wWinMain(HINSTANCE hInstance, HINSTANCE hPrevInstance, PWSTR lpCmdLine, int nCmdShow) {
    (void)hInstance;
    (void)hPrevInstance;
    (void)lpCmdLine;
    (void)nCmdShow;

    wchar_t exe_dir[PATH_BUF_LEN];
    wchar_t jar_path[PATH_BUF_LEN];
    wchar_t java_exe[PATH_BUF_LEN];
    wchar_t cmd_line[CMD_BUF_LEN];

    if (!get_exe_dir(exe_dir, PATH_BUF_LEN)) {
        MessageBoxW(
            NULL,
            L"Cannot determine Meteotablo application directory.",
            L"Meteotablo",
            MB_OK | MB_ICONERROR
        );
        return 1;
    }

    if (!join_path(jar_path, PATH_BUF_LEN, exe_dir, L"app\\Meteotablo-desktop.jar") || !file_exists(jar_path)) {
        if (!join_path(jar_path, PATH_BUF_LEN, exe_dir, L"Meteotablo-desktop.jar") || !file_exists(jar_path)) {
            MessageBoxW(
                NULL,
                L"Meteotablo-desktop.jar not found.\nExpected: app\\Meteotablo-desktop.jar",
                L"Meteotablo",
                MB_OK | MB_ICONERROR
            );
            return 1;
        }
    }

    if (!find_java_exe(exe_dir, java_exe, PATH_BUF_LEN)) {
        MessageBoxW(
            NULL,
            L"Java Runtime (jre\\bin\\javaw.exe) was not found.\nPlease keep the 'jre' folder next to Meteotablo.exe or install Java 8+.",
            L"Meteotablo",
            MB_OK | MB_ICONERROR
        );
        return 1;
    }

    size_t pos = 0;
    cmd_line[0] = L'\0';
    if (!append_str(cmd_line, CMD_BUF_LEN, &pos, L"\"") ||
        !append_str(cmd_line, CMD_BUF_LEN, &pos, java_exe) ||
        !append_str(cmd_line, CMD_BUF_LEN, &pos, L"\" -Dfile.encoding=UTF-8 -Xms64m -Xmx512m -jar \"") ||
        !append_str(cmd_line, CMD_BUF_LEN, &pos, jar_path) ||
        !append_str(cmd_line, CMD_BUF_LEN, &pos, L"\"")) {
        MessageBoxW(
            NULL,
            L"Command line path is too long.",
            L"Meteotablo",
            MB_OK | MB_ICONERROR
        );
        return 1;
    }

    STARTUPINFOW si;
    PROCESS_INFORMATION pi;
    ZeroMemory(&si, sizeof(si));
    ZeroMemory(&pi, sizeof(pi));
    si.cb = sizeof(si);

    if (!CreateProcessW(
            java_exe,
            cmd_line,
            NULL,
            NULL,
            FALSE,
            CREATE_NO_WINDOW,
            NULL,
            exe_dir,
            &si,
            &pi)) {
        DWORD err = GetLastError();
        wchar_t msg[512];
        wsprintfW(
            msg,
            L"Failed to launch Meteotablo (Windows error %lu).\nCheck jre\\bin\\javaw.exe and app\\Meteotablo-desktop.jar.",
            (unsigned long)err
        );
        MessageBoxW(NULL, msg, L"Meteotablo", MB_OK | MB_ICONERROR);
        return 1;
    }

    CloseHandle(pi.hProcess);
    CloseHandle(pi.hThread);
    return 0;
}
