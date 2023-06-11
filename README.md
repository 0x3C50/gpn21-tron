# gpn21-tron
A Kotlin implementation of the gpn21 tron game

## How does the game work?
The game is relatively simple: Build a bot to battle against other ones on a 2-dimensional play field. The one who survives the longest wins.

Imagine snake, but the snakes just keep on growing and the trail doesn't disappear. Snake meets TRON, the movie.

## How does the protocol look?
The protocol is relatively simple, everything is controlled via raw text commands sent over a TCP socket. The full protocol spec can be found in the original repo.

## What does this implementation offer?
This implementation specifically has an "attack" phase, which I haven't seen on other bots (at least so far). This makes it mildly interesting to watch, especially when it's actively trying to cut off other bots. As for win rate, it's not the best, but it can sustain a good place on the leaderboard.

This bot was also one of the first truly intelligent bots (working and running better than the other bots on day one), and the first one to implement flood fill (to my knowledge). The first working version with flood fill was made in ca. 2 hours.

## Cool, but how do I build and run this?
Building is simple, you just need a java 17 - 20 JDK, and maven. To get started, clone this repository, execute `maven clean package`, and run the `target/gpnSnakeProject-1.0-SNAPSHOT-jar-with-dependencies.jar` jarfile. **This bot does not have a GUI, you will need to run it in a terminal emulator**. Windows CMD will not show the ansi colors correctly. Use WSL, or alternatively powershell if WSL is not an option. Or just use linux like a sane person :^)