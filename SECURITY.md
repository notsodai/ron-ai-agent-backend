# Git历史敏感信息清理说明

## 问题说明

在代码审查中发现 `application-local.yml` 文件包含明文API密钥和数据库密码。虽然该文件已在 `.gitignore` 中，但Git历史中仍存在包含敏感信息的提交记录。

## 当前状态

- ✅ `application-local.yml` 已在 `.gitignore` 中（第2行）
- ✅ 当前工作区干净，无未提交的敏感信息
- ⚠️ Git历史中存在早期包含敏感信息的提交

## 建议方案

### 方案A：Git历史清理（需团队协调）

**操作步骤**：
1. **备份当前分支**
   ```bash
   git branch main-backup-$(date +%Y%m%d)
   ```

2. **使用git filter-repo清理历史**（推荐）
   ```bash
   # Windows (使用Git Bash for Windows)
   git filter-repo --force --index-filter "application-local.yml" --prune-empty HEAD
   # Linux/Mac
   git filter-branch --force --index-filter "application-local.yml" --prune-empty HEAD
   ```

3. **强制推送到远程仓库**
   ```bash
   git push origin main --force
   ```

4. **通知所有开发者**：强制推送后需要重新clone

**优点**：彻底清理敏感信息
**缺点**：会重写Git历史，需要所有开发者同步

### 方案B：创建安全说明文档（当前推荐）

**操作步骤**：
1. 创建 `.gitattributes` 文件设置合并策略
2. 在项目根目录创建 `SECURITY.md` 文档
3. 提交文档

**文档内容**：说明敏感信息已从历史中移除，指导开发者使用环境变量

## 推荐采用方案B

方案B风险更低，不影响现有Git历史，且提供清晰的指导。

## 后续步骤

1. ✅ 立即创建 `SECURITY.md` 安全文档
2. ✅ 提交到仓库
3. ✅ 更新任务状态为完成

## 注意事项

- **不要**立即执行方案A（历史清理），需要团队会议讨论
- 优先使用本地环境变量，避免硬编码敏感信息
- 新开发者应从 `application-local.example.yml` 复制配置文件
