import java.io.PrintWriter;
import java.io.File;
import java.util.Scanner;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.text.SimpleDateFormat;
import java.util.regex.Pattern;

import javax.swing.JFrame;
import java.awt.Dimension;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Graphics;
import javax.swing.JPanel;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

class EloRatings {
	private enum State { A, B, C }
	private enum Mode { LIMITED, CONSTRUCTED }
	private final String LIMITED_INPUT_DIRECTORY = "data\\limited";
	private final String CONSTRUCTED_INPUT_DIRECTORY = "data\\constructed";
	private final String DATA_FOLDER = "data\\";
	private final static double NEW_PLAYER_RATING = 1600.0;
	private final double DEFAULT_K_VALUE = 16.0;
	private final int NEW_PLAYER_MATCHES = 10; // threshold for a player to stop being new
	private final Date SIX_MONTH_AGO = new Date(System.currentTimeMillis() - 180L * 24 * 3600 * 1000);
	private Mode mode; // limited or constructed
	private Map<String, String> canonName = new HashMap<String, String>();	
	private List<Point> history = new ArrayList<Point>();
	private Map<String, Player> nameToPlayer = new HashMap<String, Player>();
	private List<Player> sortedPlayers = null;
	private Set<Player> players = null;
	private Map<Player, Date> lastPlayerAppearance = new HashMap<Player, Date>();
		
	public static void main (String[] args) throws Exception {
		if (args.length == 0) {
			System.out.println("Constructed or Limited?");
			return;
		}
		
		EloRatings elo;
		if (args[0].toLowerCase().startsWith("l")) {
			elo = new EloRatings(Mode.LIMITED);
		} else {
			elo = new EloRatings(Mode.CONSTRUCTED);
		}
		elo.showGraph();
		elo.print();
		//EloRatings.sortTournaments();
	}
	
	public void print() throws Exception {
		Map<String, Integer> nameToPosition = new HashMap<String, Integer>();
		String previousFile = "previous" + (mode == Mode.LIMITED ? "Limited" : "Constructed") + ".txt";
		Scanner sc = new Scanner(new File(DATA_FOLDER + previousFile), "UTF-8").useDelimiter(Pattern.compile("\\.?\\s+"));
		int pos; String name;
		while (sc.hasNext()) {
			pos = Integer.parseInt(sc.next());
			name = sc.next();
			nameToPosition.put(name, pos);
			sc.next(); // rating
		}
		sc.close();
		int n = 0;
		String change;
		for (Player p: sortedPlayers) {
			if (p.isActive()) {
				n++;
				if (nameToPosition.containsKey(p.getName())) {
					int oldPos = nameToPosition.get(p.getName());
					int posChange = oldPos - n;
					if (posChange > 0) {
						change = "+" + posChange;
					} else if (posChange < 0) {
						change = "" + posChange;
					} else {
						change = "-";
					}
				} else {
					change = "new";
				}
				
				System.out.println("" + n + ". " + p + " (" + change + ")");
			}
		}
	}

	public void parseLeague(String leagueFile) throws Exception {
		PrintWriter writer = new PrintWriter("leagueout.txt", "UTF-8");
		Scanner sc = new Scanner(new File(DATA_FOLDER + leagueFile), "UTF-8");
		
		int playersNum = sc.nextInt();
		
		Player[] players = new Player[playersNum];
		String line, playerName;
		line = sc.nextLine();
		for (int i = 0; i < playersNum; i++) {
			line = sc.nextLine();
			//System.out.println(line);
			players[i] = new Player(line.substring(0, line.indexOf("\t")));
			//System.out.println(players[i].getName());
		}
		sc.close();		
		
		sc = new Scanner(new File(leagueFile), "UTF-8");
		sc.next(); // int
		List<Match> matches = new ArrayList<Match>();
		String matchString;
		
		int p1wins, p2wins;
		for (int i = 0; i < playersNum; i++) {
			sc.next(); // playerName
			for (int j = 0; j < playersNum; j++) {
				matchString = sc.next();
				if (i >= j) continue;
				p1wins = Integer.parseInt(matchString.substring(0, 1));
				p2wins = Integer.parseInt(matchString.substring(2, 3));
				Match m = new Match(players[i].getName(), players[j].getName(), p1wins, p2wins);
				
				
				matches.add(m);
			}
			
		}
		sc.close();
		
		// write
		writer.println("Round 1");
		int i = 1;
		for (Match m: matches) {
			writer.println(i++);
			writer.println(m.getP1Name() + " vs " + m.getP2Name());
			if (m.getP1Wins() == m.getP2Wins()) {
				writer.println("Draw " + m.getP1Wins() + "-" + m.getP2Wins());
			} else if (m.getP1Wins() > m.getP2Wins()) {
				writer.println(m.getP1Name() + " wins " + m.getP1Wins() + "-" + m.getP2Wins());
			} else {
				writer.println(m.getP2Name() + " wins " + m.getP2Wins() + "-" + m.getP1Wins());
			}
		}
		writer.println("Edit");
		writer.close();
	}
	
	private EloRatings(Mode mode) throws Exception {
		//parseLeague("modern_s3.txt");
		//print();
		//System.exit(0);
		
		this.mode = mode;
		initCanonName();	

		List<Tournament> tournaments = getTournaments(); // get list of all matches of all tournaments of given type
		players = calcPlayersRatings(tournaments); // calc players' ratings based on all matches
		setPlayersActivity();
		
		sortedPlayers = new ArrayList<Player>(players);
		Collections.sort(sortedPlayers);
	}
	
	private void setPlayersActivity() {
		for (Point p: history) {
			lastPlayerAppearance.put(p.player, p.date);
		}
		for (Player player: players) {
			player.setLastAppearance(lastPlayerAppearance.get(player));
		}
	}
	
	public void showGraph() {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					int w = 1200, h = 900;
					JFrame frame = new JFrame();
					JPanel graph = new Graph(w, h);
					graph.setPreferredSize(new Dimension(w, h));
					String title = mode == Mode.CONSTRUCTED ? "Constructed ratings" : "Limited ratings";
					frame.setTitle(title);
					frame.add(graph);
					frame.setLocation(200, 50);	
					frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
					frame.pack();
					frame.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}
		
	private void initCanonName() {
		//canonName.put("otherName", "Canonical Name");
		canonName.put("Ablai", "Аблай");
		canonName.put("Ablay", "Аблай");
		canonName.put("Adilet", "Адилет");
		canonName.put("Adiko", "Адико");
		canonName.put("Aibek", "Айбек");
		canonName.put("Aybek", "Айбек");
		canonName.put("Akbar", "Акбар");
		canonName.put("Alexey", "Алексей");
		canonName.put("Alina", "Алина");
		canonName.put("Alisher", "Алишер");
		canonName.put("Altair", "Альтаир");
		canonName.put("Andan", "Андан");
		canonName.put("Andar", "Андан");
		canonName.put("Andrei", "Андрей");
		canonName.put("Anuar", "Ануар");
		canonName.put("Artem", "Артем");
		canonName.put("Aset", "Асет");
		canonName.put("Aya", "Ая");
		canonName.put("Vic", "Вик");
		canonName.put("Vik", "Вик");
		canonName.put("Vlad", "Влад");
		canonName.put("Vova", "Вова");
		canonName.put("Vovan", "Вова");
		canonName.put("Uvr", "Вова");
		canonName.put("Spawn", "Вова");
		canonName.put("Vladimir", "Вова");
		canonName.put("Daniyar", "Данияр");
		canonName.put("Daniar", "Данияр");
		canonName.put("Danik", "Данияр");
		canonName.put("Даник", "Данияр");
		canonName.put("Dark", "Дарк");
		canonName.put("Чемпион", "Дарк");
		canonName.put("Чампион", "Дарк");
		canonName.put("Daulet", "Даулет");
		canonName.put("Hellcat", "Даулет");
		canonName.put("Dauren", "Даурен");
		canonName.put("Igor", "Игорь");
		canonName.put("Ilka", "Илька");
		canonName.put("Il'ka", "Илька");
		canonName.put("Ильяс", "Илька");
		canonName.put("Lena", "Лена");
		canonName.put("Lindman", "Линдман");
		canonName.put("Максим", "Линдман");
		canonName.put("Макс", "Линдман");
		canonName.put("Никита", "Линдман");
		canonName.put("Арман", "Локи");
		canonName.put("Arman", "Локи");
		canonName.put("Loki", "Локи");
		canonName.put("Marat", "Марат");
		canonName.put("Matvei", "Матвей");
		canonName.put("Malik", "Малик");
		canonName.put("Mnogolikiy", "Многоликий");
		canonName.put("Mnogolikii", "Многоликий");
		canonName.put("Dauren2", "Многоликий");
		canonName.put("Nauryz", "Наурыз");
		canonName.put("Nauriz", "Наурыз");
		canonName.put("Prazdnik", "Наурыз");
		canonName.put("Nurik", "Нурик");
		canonName.put("Rex", "Рекс");
		canonName.put("Alex", "Саша");
		canonName.put("Sasha", "Саша");
		canonName.put("4cgoodstaff", "Саша");
		canonName.put("Алекс", "Саша");
		canonName.put("Slava", "Слава");
		canonName.put("Slavik", "Слава");
		canonName.put("Sorin", "Сорин");
		canonName.put("Sula", "Сула");
		canonName.put("Strannica", "Тани");
		canonName.put("Странница", "Тани");
		canonName.put("Tani", "Тани");
		canonName.put("Timba", "Тимур");
		canonName.put("Tima", "Тимур");
		canonName.put("Тима", "Тимур");
		canonName.put("Timur", "Тимур");
		canonName.put("Pod", "Тимур");
		canonName.put("Ulf", "Ульф");
		canonName.put("Zomba", "Зомба");		
	}
	
	public void sortTournaments() throws Exception {
		Scanner scanner = new Scanner(new File("combinedList.txt"));
		List<Tournament> tournaments = new ArrayList<Tournament>();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		while (scanner.hasNext()) {
			StringBuilder sb = new StringBuilder();
			sb.append(scanner.nextLine());
			sb.append("\r\n");
			String dateline = scanner.nextLine();
			sb.append(dateline);
			sb.append("\r\n");
			sb.append(scanner.nextLine());
			sb.append("\r\n");
			sb.append(scanner.nextLine());
			sb.append("\r\n");
			sb.append(scanner.nextLine());
			sb.append("\r\n");
			tournaments.add(new Tournament(sb.toString(), sdf.parse(dateline)));
		}
		Collections.sort(tournaments);
		PrintWriter writer = new PrintWriter("tournamentsSorted.txt", "UTF-8");
		for (Tournament t: tournaments) {
			writer.print(t);
		}
		scanner.close();
		writer.close();
	}
		
	private	Set<Player> calcPlayersRatings(List<Tournament> tournaments) {
		Set<Player> players = new HashSet<Player>();
		Set<Player> participatingPlayers = new HashSet<Player>();
		for (Tournament t: tournaments) {
			//System.out.println(t.toString() + " " + t.getDate().toString());
			for (Match m: t.getMatches()) {
				Player p1 = register(m.getP1Name(), players, nameToPlayer);
				Player p2 = register(m.getP2Name(), players, nameToPlayer);
				//System.out.println(m);
				//System.out.println("before: " + p1 + '\t' + p2);
				adjustRatings(p1, p2, m.getP1Wins(), m.getP2Wins());
				//System.out.println("after:  " + p1.toString() + '\t' + p2.toString());
				participatingPlayers.add(p1);
				participatingPlayers.add(p2);				
			}
			for (Player p: participatingPlayers) {
				history.add(new Point(t.getDate(), p, p.getRating()));
			}
			participatingPlayers.clear();
		}
		return players;
	}
	
	private void adjustRatings(Player p1, Player p2, int p1wins, int p2wins) {
		double r1, r2, e1, e2, s1, s2, k;
		r1 = p1.getRating();
		r2 = p2.getRating();
		e1 = 1.0 / (1.0 + Math.pow(10.0, (r2 - r1) / 400.0));
		e2 = 1.0 - e1;
		//e2 = 1.0 / (1.0 + Math.pow(10.0, (r1 - r2) / 400.0));
		if (p1wins == p2wins) { // in case of 0-0 we should not divide by 0
			s1 = 0.5;
			s2 = 0.5;
		} else {
			s1 = ((double) p1wins) / (p1wins + p2wins);
			s2 = ((double) p2wins) / (p1wins + p2wins);
		} 
		k = DEFAULT_K_VALUE;
		/*if (p1.getMatchCount() > NEW_PLAYER_MATCHES && p2.getMatchCount() > NEW_PLAYER_MATCHES) {
			k = DEFAULT_K_VALUE;
		} else {
			k = DEFAULT_K_VALUE * 2;
		}*/
		p1.incMatchCount();
		p2.incMatchCount();
		p1.adjustRatingBy(k * (s1 - e1));
		p2.adjustRatingBy(k * (s2 - e2));
	}
		
	private Player register(String playerName, Set<Player> players, Map<String, Player> nameToPlayer) {
		if (nameToPlayer.containsKey(playerName)) {
			return nameToPlayer.get(playerName);
		} else {
			Player player = new Player(playerName);
			players.add(player);
			nameToPlayer.put(playerName, player);
			return player;
		}
	}	
	
	private List<Match> getTournamentMatches(File file) throws Exception {
		//System.out.println("getTournamentMatches(" + file + ")");
		List<Match> matches = new ArrayList<Match>();
		Scanner scanner = new Scanner(file, "UTF-8");
		
		String lexeme, player1 = null, player2 = null, winner;
		int p1wins, p2wins;
		Match match;		
		State state = State.A;
		while (scanner.hasNext()) {
			lexeme = scanner.next();
			switch (state) {
			case A:
				if (lexeme.startsWith("Round") || lexeme.startsWith("Edit") || isNumeric(lexeme)) {
					break;
				}
				player1 = fixName(lexeme);
				state = State.B;
				break;
			case B:
				if (lexeme.startsWith("(") || lexeme.endsWith(")")) {
					break;
				}
				if (!lexeme.startsWith("vs")) {
					throw new Exception("Parsing error in file " + file.toString() + ": expected \"vs\" got " + lexeme);
				}
				lexeme = scanner.next();
				if (lexeme.startsWith("---")) { // bye
					lexeme = scanner.next();
					lexeme = scanner.next();
					state = State.A;
				} else {
					player2 = fixName(lexeme);
					state = State.C;
				}
				break;
			case C:
				if (lexeme.startsWith("(") || lexeme.endsWith(")")) {
					break;
				}
				if (lexeme.startsWith("Draw")) { 
					lexeme = scanner.next();
					p1wins = Integer.parseInt(lexeme.substring(0, 1));
					p2wins = Integer.parseInt(lexeme.substring(2, 3));
					match = new Match(player1, player2, p1wins, p2wins);
				} else if (lexeme.startsWith("Click")) { // no result, assume draw
					scanner.next();
					scanner.next();
					scanner.next();
					match = new Match(player1, player2, 0, 0);
				} else {
					winner = fixName(lexeme);
					lexeme = scanner.next();
					if (!lexeme.startsWith("wins")) {
						throw new Exception("Parsing error in file " + file.toString() + ": expected \"wins\" got " + lexeme);
					}
					lexeme = scanner.next();
					p1wins = Integer.parseInt(lexeme.substring(0, 1));
					p2wins = Integer.parseInt(lexeme.substring(2, 3));
					if (player2.equals(winner)) {
						player2 = player1;
					}
					match = new Match(winner, player2, p1wins, p2wins);
				}
				matches.add(match);
				//System.out.println(match);
				state = State.A;
				break;
			}
		}
		scanner.close();
		if (matches.size() == 0) {
			throw new Exception("Error: file " + file.toString() + " doesn't contain any data");
		}
		return matches;
	}
	
	private String fixName(String origName) {
		String name = origName.substring(0, 1).toUpperCase() + origName.substring(1);
		if (canonName.containsKey(name)) {
			return canonName.get(name);
		}
		return name;
	}
	
	private List<Tournament> getTournaments() throws Exception {
		File dir = new File(mode == Mode.LIMITED ? LIMITED_INPUT_DIRECTORY : CONSTRUCTED_INPUT_DIRECTORY);
			
		List<Tournament> tournaments = makeTournaments();
		for (Tournament t: tournaments) {
			String filename = t.getID() + ".txt";
			t.setMatches(getTournamentMatches(new File(dir, filename)));
		}
		
		return tournaments;
	}
	
	private List<Tournament> makeTournaments() throws Exception {
		Scanner scanner = new Scanner(new File("data\\tournaments.txt"));
		List<Tournament> tournaments = new ArrayList<Tournament>();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
			
		while (scanner.hasNext()) {
			String tournamentID = scanner.next();
			String tournamentType = scanner.next();
			scanner.next(); // tournament attr 2
			scanner.nextLine(); // end of line
			Date tournamentDate = sdf.parse(scanner.nextLine());
			scanner.nextLine(); // location
			scanner.nextLine(); // "Draft"
			scanner.nextLine(); // number of participants
			if (mode == Mode.LIMITED) {
				if (tournamentType.equals("draft") || tournamentType.equals("sealed")) {
					tournaments.add(new Tournament(tournamentID, tournamentDate));
				}
			} else {
				if (tournamentType.equals("constructed")) {
					tournaments.add(new Tournament(tournamentID, tournamentDate));
				}
			}
		}
		scanner.close();
		return tournaments;
	}		
			
	private static boolean isNumeric(String s) {
		if (s.matches("\\d+")) {
			return true;
		}
		return false;
	}
	
	private static double round(double x, double step) {
		return Math.floor(x / step + 0.5) * step;
	}
	
	private static int round(double x) {
		return (int)Math.floor(x + 0.5);
	}
	
	class Graph extends JPanel {
		private int startX = 50;
		private int endX;
		private int startY = 50;
		private int endY;
		private int maxRating = 0;
		private int minRating = 3000;
		private Date minDate = new Date(); // current time
		private Date maxDate = new Date(0); // 1970
		private Map<Player, Point> lastPoint = new HashMap<Player, Point>();
		private Color[] colors;
		private int usedColors = 0;
		private Map<Player, Color> playerToColor = new HashMap<Player, Color>();
		private Calendar tmpDate = new GregorianCalendar();
		private Color LightGrey = Color.getHSBColor(0.0f / 12, 0.0f, 0.8f);
				
		public Graph(int w, int h) {
			super();
			
			endX = w - 100;
			endY = h - 50;
			
			initColors();
			
			for (Point p: history) {
				if (!p.player.isActive()) {
					continue;
				}
				if (p.rating > maxRating) {
					maxRating = (int)p.rating;
				}
				if (p.rating < minRating) {
					minRating = (int)p.rating;
				}
				if (p.date.before(minDate)) {
					minDate = p.date;
				}
				if (p.date.after(maxDate)) {
					maxDate = p.date;
				}
			}
			minRating = minRating - 30;
			maxRating = maxRating + 30;
		}
		
		private void initColors() {
			colors = new Color[]{
			  Color.getHSBColor(0.0f, 1.0f, 1.0f), // red
			  Color.getHSBColor(1.0f / 12, 1.0f, 1.0f), // orange
			  Color.getHSBColor(1.9f / 12, 1.0f, 0.9f), // yellow
			  Color.getHSBColor(334.0f / 360, 0.5f, 1.0f), // pink
			  Color.getHSBColor(4.0f / 12, 1.0f, 0.95f), // green
			  Color.getHSBColor(4.0f / 12, 1.0f, 0.5f), // dark green
			  Color.getHSBColor(5.0f / 12, 1.0f, 0.75f), // seawave darker
			  Color.getHSBColor(6.0f / 12, 1.0f, 0.9f), // goluboi
			  Color.getHSBColor(6.0f / 12, 1.0f, 0.5f), // dark goluboi
			  Color.getHSBColor(7.0f / 12, 1.0f, 1.0f), // light blue
			  Color.getHSBColor(8.0f / 12, 1.0f, 1.0f), // dark blue
			  Color.getHSBColor(9.0f / 12, 1.0f, 1.0f), // violet
			  Color.getHSBColor(10.0f / 12, 1.0f, 1.0f), // margenta
			  Color.getHSBColor(11.0f / 12, 1.0f, 0.70f), // margenta? darker
			  Color.getHSBColor(0.0f / 12, 1.0f, 0.0f), // black
			  Color.getHSBColor(0.0f / 12, 0.0f, 0.5f), // grey
			  Color.getHSBColor(2.0f / 9, 1.0f, 0.85f), // yellow+ green
			};
		}
		
		private Color getPlayerColor(Player player) {
			Color c = null;
			if (playerToColor.containsKey(player)) {
				return playerToColor.get(player);
			} else {
				c = colors[usedColors % colors.length];
				playerToColor.put(player, c);
				usedColors++;
				return c;
			}
		}
		
		@Override
		public void paintComponent(Graphics g) {
			super.paintComponent(g);
			
			Graphics2D g2d = (Graphics2D)g;
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			
			// horizontal marks
			int x, y;
			double stepX = (double)(endX - startX) / (maxDate.getTime() - minDate.getTime()); // pixels per millisecond
			double stepY = (double)(endY - startY) / (maxRating - minRating);
			for (int rating = minRating / 50 * 50 + 50; rating <= maxRating; rating += 50) {
				x = startX;
				y = endY - round(stepY * (rating - minRating));
				if (rating % 50 == 0) {
					g.setColor(LightGrey);
					g.drawLine(startX, y, endX, y);
					g.setColor(Color.BLACK);					
				}
				g.drawString(String.valueOf(rating), x - 30, y + 5);
				g.drawLine(x - 2, y, x + 2, y);
			}
			
			// vertical marks
			tmpDate.setTime(minDate);
			tmpDate.add(Calendar.MONTH, 1);
			tmpDate.set(Calendar.DAY_OF_MONTH, 1);
			for (; tmpDate.getTimeInMillis() < maxDate.getTime(); tmpDate.add(Calendar.MONTH, 1)) {
				x = startX + round(stepX * (tmpDate.getTimeInMillis() - minDate.getTime()));
				y = endY;
				if (tmpDate.get(Calendar.MONTH) % 3 == 0) {
					g.setColor(LightGrey);
					g.drawLine(x, startY, x, endY);
					g.setColor(Color.BLACK);
				}
				if (tmpDate.get(Calendar.MONTH) % 12 == 0) {
					g.drawString(String.valueOf(tmpDate.get(Calendar.YEAR)), x - 15, y + 20);
					g.drawLine(x, y - 4, x, y + 4);
				}
				g.drawLine(x, y - 2, x, y + 2);				
			}
			
			// main graph
			int x1, y1, x2, y2;
			float hue;
			lastPoint.clear();
			for (Point p: history) {
				if (p.player.getMatchCount() <= NEW_PLAYER_MATCHES || !p.player.isActive()) {
					continue;
				}
				if (lastPoint.containsKey(p.player)) {
					g.setColor(getPlayerColor(p.player));
					long dt = lastPoint.get(p.player).date.getTime() - minDate.getTime();
					x1 = startX + round(stepX * dt);
					y1 = endY - round(stepY * (lastPoint.get(p.player).rating - minRating));
					dt = p.date.getTime() - minDate.getTime();
					x2 = startX + round(stepX * dt);
					y2 = endY - round(stepY * (p.rating - minRating));
					g.drawLine(x1, y1, x2, y2);
				}
				lastPoint.put(p.player, p);
			}
			
			// Ox Oy
			g.setColor(Color.BLACK);
			g.drawLine(startX - 10, endY, endX + 10, endY);
			g.drawLine(startX, startY - 10, startX, endY + 10);
			
			// player names
			for (Map.Entry<String, Player> entry: nameToPlayer.entrySet()) {
				String playerName = entry.getKey();
				Player player = entry.getValue();
				if (player.getMatchCount() <= NEW_PLAYER_MATCHES || !player.isActive()) {
					continue;
				}
				g.setColor(getPlayerColor(player));
				long dt = lastPoint.get(player).date.getTime() - minDate.getTime();
				x1 = startX + round(stepX * dt);
				y = endY - round(stepY * (lastPoint.get(player).rating - minRating));
				x2 = endX + 20;
				g.drawLine(x1, y, x2, y);
				g.drawString(playerName, x2 + 2, y + 5);
			}
		}
	}
	
	class Match {
		private String p1name;
		private String p2name;
		private int p1wins;
		private int p2wins;

		public Match(String p1name, String p2name, int p1wins, int p2wins) throws Exception {
			if (p1wins < 0 || p1wins > 2 || p2wins < 0 || p2wins > 2 || p1wins + p2wins > 3) {
				throw(new Exception("Match creation: illegal p1wins, p2wins values: " + p1wins + ", " + p2wins));
			}
			this.p1name = p1name;
			this.p2name = p2name;
			this.p1wins = p1wins;
			this.p2wins = p2wins;
		}
		
		public String getP1Name() {
			return p1name;
		}
		public String getP2Name() {
			return p2name;
		}
		public int getP1Wins() {
			return p1wins;
		}
		public int getP2Wins() {
			return p2wins;
		}
			
		@Override
		public String toString() {
			if (p1wins == p2wins) {
				return String.format("%s draw %s %d:%d", p1name, p2name, p1wins, p2wins);
			} else if (p1wins > p2wins) {
				return String.format("%s wins %s %d:%d", p1name, p2name, p1wins, p2wins);
			} else {
				return String.format("%s wins %s %d:%d", p2name, p1name, p2wins, p1wins);
			}
		}
	}

	class Player implements Comparable<Player> {
		private String name;
		private double rating;
		private int matchCount = 0;
		private Date lastAppearance = null;
		
		public Player(String name) {
			this.name = name;
			rating = EloRatings.NEW_PLAYER_RATING;
		}
		
		public boolean isActive() {
			if (lastAppearance == null) {
				System.out.println("Player " + name + "'s last appearance is accessed but not set");
				return false;
			}
			if (lastAppearance.after(SIX_MONTH_AGO)) {
				return true;
			} else {
				return false;
			}
		}
		
		public void setLastAppearance(Date lastAppearance) {
			this.lastAppearance = lastAppearance;
		}
		
		public String getName() {
			return name;
		}
		public double getRating() {
			return rating;
		}
		public void adjustRatingBy(double dr) {
			rating += dr;
		}
		public int getMatchCount () {
			return matchCount;
		}
		public void incMatchCount() {
			matchCount++;
		}
		
		@Override 
		public int compareTo(Player p) {
			return this.rating > p.rating ? -1 : 1;
		}
		
		@Override 
		public String toString() {
			return String.format("%-10s %.0f", name, rating);
		}
		
		@Override
		public int hashCode() {
			return name.hashCode();
		}
		
		@Override
		public boolean equals(Object o) {
			Player p = (Player) o;
			return p.name.equals(name);
		}
	}

	class Tournament implements Comparable<Tournament> {
		private String id;
		private Date date;
		private List<Match> matches = new ArrayList<Match>();
		
		public Date getDate() {
			return date;
		}
		
		public String getID() {
			return id;
		}
		
		public Tournament(String id, Date date) {
			this.id = id;
			this.date = date;
		}
		
		public void setMatches(List<Match> matchList) {
			matches = matchList;
		}	
		public List<Match> getMatches() {
			return matches;
		}
		
		@Override
		public int compareTo(Tournament t) {
			return this.date.compareTo(t.date);
		}
		
		@Override
		public String toString() {
			return id;
		}
	}
	
	class Point {
		public Date date;
		public Player player;
		public double rating;
		
		public Point(Date date, Player player, double rating) {
			this.date = date;
			this.player = player;		
			this.rating = rating;
		}
	}
}

