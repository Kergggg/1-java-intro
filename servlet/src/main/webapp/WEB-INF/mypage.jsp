<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <style>
        body {
            font-family: 'Courier New', Courier, monospace;
            margin: 20px;
            background-color: white;
        }

        .container {
            max-width: 1000px;
            margin: 0 auto;
        }

        h1 {
            font-size: 24px;
            font-family: 'Courier New', Courier, monospace;
            font-weight: bold;
            margin-bottom: 20px;
        }

        .time-stamp {
            font-family: monospace;
            color: #000;
            margin-bottom: 15px;
            font-size: 14px;
        }

        .current-path {
            font-family: monospace;
            margin: 15px 0;
            font-size: 14px;
        }

        .up-link {
            display: block;
            margin-bottom: 15px;
            font-family: monospace;
            font-size: 14px;
        }

        .up-link a {
            text-decoration: none;
            color: #0000EE;
        }

        .up-link a:hover {
            text-decoration: underline;
        }

        table {
            width: 100%;
            border-collapse: collapse;
            font-family: monospace;
            font-size: 14px;
            table-layout: auto;
        }

        th, td {
            padding: 4px 8px;
            text-align: left;
            vertical-align: top;
        }

        /* Фиксированная ширина для колонок */
        th:nth-child(1), td:nth-child(1) {
            width: 60%;
        }

        th:nth-child(2), td:nth-child(2) {
            width: 15%;
            text-align: right;
            white-space: nowrap;
        }

        th:nth-child(3), td:nth-child(3) {
            width: 25%;
            white-space: nowrap;
        }

        th {
            font-weight: bold;
            border-bottom: 1px solid #000;
            background-color: transparent;
        }

        .file-link, .dir-link {
            text-decoration: none;
            color: #0000EE;
        }

        .file-link:hover, .dir-link:hover {
            text-decoration: underline;
        }

        .dir-link {
            font-weight: normal;
        }

        .size-column {
            text-align: right;
            white-space: nowrap;
        }

        .icon {
            margin-right: 5px;
        }
    </style>
</head>
<body>
<div class="container">
    <div style="text-align: right; margin-bottom: 20px;">
        <a href="#" onclick="logout()" style="color: #0000EE;">Выйти</a>
    </div>

    <script>
        function logout() {
            fetch('${pageContext.request.contextPath}/api/logout', {
                method: 'POST'
            }).then(function(response) {
                if (response.status === 200) {
                    window.location.href = '${pageContext.request.contextPath}/';
                }
            });
        }
    </script>

    <div class="time-stamp">
        ${currentTime}
    </div>

    <div class="current-path">
        ${currentPath}
    </div>

    <c:if test="${parentPath != null}">
        <div class="up-link">
            <a href="${pageContext.request.contextPath}/files?path=${parentPath}">Вверх</a>
        </div>
    </c:if>

    <table>
        <thead>
            <tr>
                <th>Файл</th>
                <th>Размер</th>
                <th>Дата</th>
            </tr>
        </thead>
        <tbody>
            <c:forEach items="${files}" var="file">
                <tr>
                    <td style="word-break: break-all;">
                        <c:choose>
                            <c:when test="${file.directory}">
                                <a href="${pageContext.request.contextPath}/files?path=${file.path}" class="dir-link">
                                    <span class="icon">📁</span> ${file.name}/
                                </a>
                            </c:when>
                            <c:otherwise>
                                <a href="${pageContext.request.contextPath}/files?download=${file.path}" class="file-link">
                                    <span class="icon">📄</span> ${file.name}
                                </a>
                            </c:otherwise>
                        </c:choose>
                    </td>
                    <td class="size-column">${file.size}</td>
                    <td class="date-column">${file.lastModified}</td>
                </tr>
            </c:forEach>

            <c:if test="${empty files}">
                <tr>
                    <td colspan="3" style="text-align: center;">Директория пуста</td>
                </tr>
            </c:if>
        </tbody>
    </table>
</div>
</body>
</html>
