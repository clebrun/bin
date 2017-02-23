#!/usr/bin/env bash

# don't use -n for git log if no arguments given
# check git show args to cut down on visual noise?
git log --oneline -n $1 | grep -v Merge | cut -d' ' -f1 | tac | xargs git show
