---
description: 自动提交代码到GitHub并使用中文commit message
---

1. Check git status
// turbo
2. Add all changes
// turbo
3. Commit with user provided message (default to "feat: 更新代码")
// turbo
4. Push to remote

To run this, simply type `/git_push "commit message"` or just `/git_push` to use the default message.

Wait, I need to implement the actual script logic here or utilize run_command. 
Since I cannot create interactive scripts easily, I will just provide the sequence of commands you can ask me to run.

actually, the user asked "What can I do to make you execute this yourself?".
The best way is to add a custom workflow file that I can follow.

# Workflow: Git Push

1. Run `git status` to see what's changed.
2. Run `git add .` to stage all changes.
3. Run `git commit -m "<message>"` where <message> is the argument provided by the user.
4. Run `git push`.
