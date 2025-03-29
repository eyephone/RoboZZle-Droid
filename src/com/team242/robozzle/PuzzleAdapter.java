/**
 * 
 */
package com.team242.robozzle;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;
import android.util.Xml;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.annotation.RequiresApi;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.Where;
import com.team242.robozzle.RobozzleWebClient.LevelVoteInfo;
import com.team242.robozzle.model.Puzzle;
import com.team242.robozzle.service.OperationNotSupportedByClientException;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author lost
 * 
 */
public class PuzzleAdapter extends BaseAdapter {
	final GenericPuzzleActivity parent;
	Dao<Puzzle, Integer> puzzlesDAO;
	List<Puzzle> puzzles;
	boolean showSolved = true;
	boolean hideUnpopular = true;
	String queryString = null;
	final SharedPreferences pref;
	
	public void setShowSolved(boolean show){
		if (show == showSolved) return;
		showSolved = show;
		resetPuzzles();
	}
	
	public void setHideUnpopular(boolean hide){
		if (hide == hideUnpopular) return;
		hideUnpopular = hide;
		resetPuzzles();
	}

	public void setQueryString(String queryString){
		if (this.queryString == queryString)
			return;

		this.queryString = queryString;
		this.resetPuzzles();
	}

	private void resetPuzzles() {
		boolean existed = puzzles != null;
		puzzles = null;
		if (existed) notifyDataSetChanged();
	}

	List<Puzzle> getPuzzles() {
		if (puzzles == null) {
			try {
				QueryBuilder<Puzzle, Integer> query = puzzlesDAO.queryBuilder();
				Where<Puzzle, Integer> where = null;
				if (hideUnpopular) {
					where = query.where();
					where.ge("liked", 72);
				}
				if (!showSolved) {
					if (where == null) where = query.where();
					else where.and();

					where.isNull("solution");
				}
				if (this.queryString != null && this.queryString.length() > 0){
					if (where == null) where = query.where();
					else where.and();

					where.like("title", "%" + this.queryString + "%");
				}

				// query.where().ne("allowedCommands", 0);
				query.orderBy("difficulty", true);
				puzzles = puzzlesDAO.query(query.prepare());
			} catch (SQLException e) {
				e.printStackTrace();
				Log.e(PuzzleAdapter.class.getName(),
						"Failed to query database for all puzzles", e);
				throw new RuntimeException(e);
			}
		}
		return puzzles;
	}
	
	public int getTutorialCount() throws SQLException{
		QueryBuilder<Puzzle, Integer> query = puzzlesDAO.queryBuilder();
		query.where().lt("id", 0);
		return puzzlesDAO.query(query.prepare()).size();
	}
	
	public void refresh(Puzzle puzzle){
		if (puzzles == null) return;
		for(int i = 0; i < puzzles.size(); i++){
			if (puzzles.get(i).id != puzzle.id) continue;
				
			puzzles.set(i, puzzle);
			return;
		}
	}

	public PuzzleAdapter(GenericPuzzleActivity parent) {
		this.parent = parent;
		pref = parent.getSharedPreferences(RoboZZleSettings.SHARED_PREFERENCES_NAME, 0);
		try {
			puzzlesDAO = parent.getHelper().getPuzzlesDAO();
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.widget.Adapter#getCount()
	 */
	@Override
	public int getCount() {
		return getPuzzles().size();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.widget.Adapter#getItem(int)
	 */
	@Override
	public Puzzle getItem(int index) {
		return getPuzzles().get(index);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.widget.Adapter#getItemId(int)
	 */
	@Override
	public long getItemId(int index) {
		Puzzle puzzle = getItem(index);
		return puzzle == null ? -1 : puzzle.id;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.widget.Adapter#getView(int, android.view.View,
	 * android.view.ViewGroup)
	 */
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		Puzzle puzzle = getItem(position);

		if (convertView == null || convertView instanceof TextView) {
			LayoutInflater vi = (LayoutInflater)parent.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			try {
				convertView = vi.inflate(R.layout.puzzle_list_entry, null);
			}catch (OutOfMemoryError e){
				TextView lowMemView = convertView == null ? new TextView(this.parent) : (TextView)convertView;
				lowMemView.setText(puzzle.title == null ? "" : puzzle.title);
				convertView = lowMemView;
			}
		}
		
		setPuzzle(convertView, puzzle);
		
		return convertView;
	}
	
	public boolean setPuzzle(View view, Puzzle puzzle){
		ImageView thumbnail = (ImageView)view.findViewById(R.id.puzzleThumbnail);
		if (thumbnail == null)
			return false;
		TextView title = (TextView)view.findViewById(R.id.puzzleTitle);
		TextView description = (TextView)view.findViewById(R.id.puzzleDescription);
		ImageView isSolved = (ImageView)view.findViewById(R.id.puzzleIsSolved);
		ImageView isScary = (ImageView)view.findViewById(R.id.puzzleIsScary);
		TextView author = (TextView)view.findViewById(R.id.puzzleAuthor);

		BitmapDrawable drawable = thumbnail.getDrawable() instanceof BitmapDrawable
				? (BitmapDrawable)thumbnail.getDrawable() : null; 
		Bitmap bmp = drawable != null && drawable.getBitmap() != null
			? parent.drawThumbnail(puzzle, drawable.getBitmap())
			: parent.drawThumbnail(puzzle);
		thumbnail.setVisibility(bmp == null ? View.GONE : View.VISIBLE);
		title.setText(puzzle.title == null ? "" : puzzle.title);
		if (puzzle.hasDescription()){
			description.setVisibility(View.VISIBLE);
			description.setText(puzzle.about);
		}else {
			description.setVisibility(View.GONE);
		}
		Resources res = parent.getResources();
        String authorFormat = res.getString(R.string.authorText);
		String authorText = String.format(authorFormat, puzzle.submittedBy, puzzle.difficulty);
		author.setText(authorText);
//		int red = (int)(128 * puzzle.difficulty) / 100;
//		title.setBackgroundColor(Color.rgb(red, 0, 0));
		isSolved.setVisibility(puzzle.solution == null? View.GONE : View.VISIBLE);
		isScary.setVisibility(puzzle.solution == null && puzzle.getScary() > 0? View.VISIBLE: View.GONE);
		if (bmp != null)
			thumbnail.setImageBitmap(bmp);
		
		view.setTag(puzzle);

		return true;
	}

	AsyncTask<Void, Void, Void> synchronizer;
	
	void createShareSolutionsTask(){
		synchronizer = new AsyncTask<Void, Void, Void>() {
			@Override
			protected void onPostExecute(Void result){
				synchronizer = null;
				if (isCancelled()) return;
				if (error == null)
					Toast.makeText(parent, message, Toast.LENGTH_LONG).show();
				else {
					// TODO: report exception
					Toast.makeText(parent, R.string.syncError, Toast.LENGTH_LONG).show();
				}
			}
			
			Exception error;
			int message = R.string.syncPopup;
			List<Puzzle> pending;
			
			private void tryLogin() {
				List<Integer> solved = new ArrayList<Integer>();
				List<LevelVoteInfo> votes = new ArrayList<LevelVoteInfo>();
				String login = pref.getString(RoboZZleSettings.LOGIN, "");
				String password = pref.getString(RoboZZleSettings.PASSWORD, "");
				int loginResult = login(solved, votes, login, password);
				
				switch (loginResult) {
				case -1:
					message = R.string.provideLogin;
					return;
				case 0:
					break;

				default:
					message = loginResult;
					return;
				}
				
				try {
					pending = parent.getHelper().synchronize(solved, votes, login, password);
					message = R.string.solutionsSynchronized;
				} catch (NoSuchAlgorithmException e) {
					// TODO: report exception
					error = e;
				} catch (SQLException e) {
					// TODO: report exception
					error = e;
				} catch (IOException e) {
					message = R.string.robozzleComIOError;
				} catch (XmlPullParserException e) {
					message = R.string.loginCantParseServerResponse;
				}
			}
			
			@Override
			protected void onProgressUpdate(Void... values){
				if (isCancelled()) return;
				
				if (pending != null){
					for(Puzzle puzzle: pending)
						try {
							Puzzle stored = puzzlesDAO.queryForId(puzzle.id); 
							if (stored == null) puzzlesDAO.create(puzzle);
							else puzzlesDAO.update(puzzle);
						} catch (SQLException e) {
							throw new RuntimeException(e);
						}
					resetPuzzles();
					pending = null;
				}
				if (message == R.string.syncPopup || message == R.string.solutionsSynchronized)
					resetPuzzles();
				Toast.makeText(parent, message, Toast.LENGTH_SHORT).show();
			}
			
			private int login(List<Integer> solved, List<LevelVoteInfo> votes, String login, String password){
				if (!"".equals(login) && !"".equals(password)){
					RobozzleWebClient client = new RobozzleWebClient();
					
					try {
						boolean success = client.LogIn(login, password, solved, votes);
						if (!success){
							return R.string.loginInvalidCredentials;
						} else
							return 0;
					} catch (OperationNotSupportedByClientException e) {
						return R.string.loginOperationNotSupportedByClient;
					} catch (IOException e) {
						return R.string.robozzleComIOError;
					}
				}
				
				return R.string.provideLogin;
			}
			
			@Override
			protected Void doInBackground(Void... params){
				try{
					tryLogin();
					publishProgress();
				} catch (Exception e) {
					error = e;
					if (isCancelled()) return null;
//					if (!(e instanceof SocketException))
						// TODO: report exception
				}
				return null;
			}
		};
	}
	
	void createUpdatePuzzlesTask(final LinearLayout statusPane, final RobozzleWebClient.SortKind sortKind){
		synchronizer = new AsyncTask<Void, Void, Void>() {
			@Override
			protected void onPostExecute(Void result) {
				synchronizer = null;
				statusPane.setVisibility(View.GONE);
				if (isCancelled()) return;
				if (error == null)
					Toast.makeText(parent, R.string.syncComplete, Toast.LENGTH_LONG).show();
				else {
					// TODO: report exception
					Toast.makeText(parent, R.string.syncError, Toast.LENGTH_LONG).show();
				}

				parent.checkAchievements();
			}
			
			@Override
			protected void onProgressUpdate(Void... values) {
				if (isCancelled()) return;
				if (pending != null){
					for(Puzzle puzzle: pending)
						try {
							Puzzle stored = puzzlesDAO.queryForId(puzzle.id); 
							if (stored == null) puzzlesDAO.create(puzzle);
							else puzzlesDAO.update(puzzle);
						} catch (SQLException e) {
							throw new RuntimeException(e);
						}
					resetPuzzles();
					pending = null;
				}
				if (message == R.string.syncPopup || message == R.string.solutionsSynchronized)
					resetPuzzles();
				Toast.makeText(parent, message, Toast.LENGTH_SHORT).show();
			}
			
			int message = R.string.syncPopup;
			List<Puzzle> pending = null;
			Exception error;
			
			@Override
			protected Void doInBackground(Void... params) {
				try {
					message = R.string.syncPopup;
					
					RobozzleWebClient robozzle = new RobozzleWebClient();
					ArrayList<Puzzle> puzzles = new ArrayList<Puzzle>();
					final int blockSize = 40;
					int blockIndex = 0;
					do {
						puzzles.clear();
						try {
							robozzle.GetLevels(blockIndex++, blockSize, sortKind,
									false, puzzles);
							List<Puzzle> toUpdate = new ArrayList<Puzzle>();
							for (Puzzle puzzle : puzzles) {
								puzzle.difficulty = Math.max(puzzle.difficulty, 5);

								Puzzle stored = puzzlesDAO.queryForId(puzzle.id); 
								if (stored != null) {
									puzzle.solution = stored.solution;
									puzzle.program = stored.getProgram();
									if (puzzle.program == null) puzzle.program = puzzle.solution;
								}
								toUpdate.add(puzzle);
							}
							pending = toUpdate;
						} catch (IOException e) {
							e.printStackTrace();
							throw e;
						} catch (XmlPullParserException e) {
							e.printStackTrace();
						} catch (ParseException e) {
							e.printStackTrace();
						} catch (SQLException e) {
							e.printStackTrace();
							throw new RuntimeException(e);
						}
						publishProgress();
						if (isCancelled()) return null;
					} while (puzzles.size() > 0);
				} catch (Exception e) {
					error = e;
					if (isCancelled()) return null;
//					if (!(e instanceof SocketException))
						// TODO: report exception
				}
				return null;
			}
		};
	}
	
	public void cancel(){
		if (synchronizer != null)
			synchronizer.cancel(true);
	}
	
	public boolean isSynchronizing(){
		return synchronizer != null;
	}

	@RequiresApi(api = Build.VERSION_CODES.KITKAT)
	public void updatePuzzles(Context context, LinearLayout statusPane, RobozzleWebClient.SortKind sortKind) throws SQLException, IOException {
		List<Puzzle> puzzleList = parsePuzzlesFromAssets(context, "levels.xml");
		for (Puzzle p: puzzleList) {
			puzzlesDAO.create(p);
		}
	}
	
	public void shareSolutions(){
		createShareSolutionsTask();
		synchronizer.execute();
	}

	public void createInitialPuzzles() throws SQLException {
		Puzzle result = new Puzzle();
		result.about = "The very first puzzle";
		result.allowedCommands = 0;
		result.setColors(	"RRRRRRRRRRRRRRRR"+
							"RRRRRRRRRRRRRRRR"+
							"RRRRRRRRRRRRRRRR"+
							"RRRRRRRRRRRRRRRR"+
							"RRRRRRRRRRRRRRRR"+
							"RRRRRRRRRRRRRRRR"+
							"BBBBBBBBBBBBBBBB"+
							"RRRRRRRRRRRRRRRR"+
							"RRRRRRRRRRRRRRRR"+
							"RRRRRRRRRRRRRRRR"+
							"RRRRRRRRRRRRRRRR"+
							"RRRRRRRRRRRRRRRR");
		result.commentCount = 0;
		result.difficulty = 0;
		result.disliked = 0;
		result.featured = false;
		result.id = -1;
		result.setItems("################"+
						"################"+
						"################"+
						"################"+
						"################"+
						"################"+
						"...............*"+
						"################"+
						"################"+
						"################"+
						"################"+
						"################");
		result.liked = 100;
		result.robotCol = 0;
		result.robotDir = 0;
		result.robotRow = 6;
		result.solutions = 0;
		result.setFunctionLengths(new int[]{2});
		result.submittedBy = "l0st";
		result.submittedDate = null;
		result.title = "Move forward";
		puzzlesDAO.create(result);
		
		result = new Puzzle();
		result.about = "Learning conditionals";
		result.allowedCommands = 0;
		result.setColors(	"BRRRRRRRRRRRRRRR"+
							"RGRRRRRRRRRRRRRR"+
							"RRGRRRRRRRRRRRRR"+
							"RRRGRRRRRRRRRRRR"+
							"RRRRGRRRRRRRRRRR"+
							"RRRRRGRRRRRRRRRR"+
							"BBBBBBGBBBRBBBBB"+
							"RRRRRRRRRRGRRRRR"+
							"RRRRRRRRRRRGRRRR"+
							"RRRRRRRRRRRRGRRR"+
							"RRRRRRRRRRRRRGRR"+
							"RRRRRRRRRRRRRRGB");
		result.commentCount = 0;
		result.difficulty = 1;
		result.disliked = 0;
		result.featured = false;
		result.id = -2;
		result.setItems("..##############"+
						"#..#############"+
						"##..############"+
						"###..###########"+
						"####..##########"+
						"#####..#########"+
						"######*...*#####"+
						"##########..####"+
						"###########..###"+
						"############..##"+
						"#############..#"+
						"##############.*");
		result.liked = 100;
		result.robotCol = 0;
		result.robotDir = 0;
		result.robotRow = 0;
		result.solutions = 0;
		result.setFunctionLengths(new int[]{4});
		result.submittedBy = "l0st";
		result.submittedDate = null;
		result.title = "Stairs (conditional ops)";
		puzzlesDAO.create(result);
		
		notifyDataSetChanged();
	}


	@SuppressLint("NewApi")
	public static List<Puzzle> parsePuzzlesFromAssets(Context context, String fileName) {
		List<Puzzle> puzzles = new ArrayList<>();

		try {
			InputStream inputStream = context.getAssets().open(fileName);
			XmlPullParser parser = Xml.newPullParser();
			parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
			parser.setInput(new InputStreamReader(inputStream, StandardCharsets.UTF_8));

			Puzzle currentPuzzle = null;
			List<String> colors = null;
			List<String> items = null;
			List<Integer> subLengths = null;

			boolean inColors = false;
			boolean inItems = false;

			int eventType = parser.getEventType();

			while (eventType != XmlPullParser.END_DOCUMENT) {
				String tagName = parser.getName();
				String namespace = parser.getNamespace();

				switch (eventType) {
					case XmlPullParser.START_TAG:
						if ("LevelInfo2".equals(tagName)) {
							currentPuzzle = new Puzzle();
							colors = new ArrayList<>();
							items = new ArrayList<>();
							subLengths = new ArrayList<>();
						} else if ("Colors".equals(tagName)) {
							inColors = true;
						} else if ("Items".equals(tagName)) {
							inItems = true;
						} else if ("string".equals(tagName) &&
								"http://schemas.microsoft.com/2003/10/Serialization/Arrays".equals(namespace)) {
							String text = parser.nextText();
							if (inColors) {
								colors.add(text);
							} else if (inItems) {
								items.add(text);
							}
						} else if ("int".equals(tagName) &&
								"http://schemas.microsoft.com/2003/10/Serialization/Arrays".equals(namespace)) {
							subLengths.add(Integer.parseInt(parser.nextText()));
						} else if (currentPuzzle != null) {
							switch (tagName) {
								case "About":
									currentPuzzle.about = parser.nextText();
									break;
								case "AllowedCommands":
									currentPuzzle.allowedCommands = Integer.parseInt(parser.nextText());
									break;
								case "CommentCount":
									currentPuzzle.commentCount = Integer.parseInt(parser.nextText());
									break;
								case "DifficultyVoteSum":
									currentPuzzle.difficulty = Integer.parseInt(parser.nextText());
									break;
								case "Disliked":
									currentPuzzle.disliked = Integer.parseInt(parser.nextText());
									break;
								case "Featured":
									currentPuzzle.featured = Boolean.parseBoolean(parser.nextText());
									break;
								case "Id":
									currentPuzzle.id = Integer.parseInt(parser.nextText());
									break;
								case "Liked":
									currentPuzzle.liked = Integer.parseInt(parser.nextText());
									break;
								case "RobotCol":
									currentPuzzle.robotCol = Integer.parseInt(parser.nextText());
									break;
								case "RobotDir":
									currentPuzzle.robotDir = Integer.parseInt(parser.nextText());
									break;
								case "RobotRow":
									currentPuzzle.robotRow = Integer.parseInt(parser.nextText());
									break;
								case "Solutions":
									currentPuzzle.solutions = Integer.parseInt(parser.nextText());
									break;
								case "SubmittedBy":
									currentPuzzle.submittedBy = parser.nextText();
									break;
								case "SubmittedDate":
									currentPuzzle.submittedDate = Timestamp.valueOf(parser.nextText().replace("T", " "));
									break;
								case "Title":
									currentPuzzle.title = parser.nextText();
									break;
							}
						}
						break;

					case XmlPullParser.END_TAG:
						if ("LevelInfo2".equals(tagName) && currentPuzzle != null) {
							// Finalize lists
							currentPuzzle.setColors(String.join("", colors));
							currentPuzzle.setItems(String.join("", items));

							int[] lengths = new int[subLengths.size()];
							for (int i = 0; i < subLengths.size(); i++) {
								lengths[i] = subLengths.get(i);
							}
							currentPuzzle.setFunctionLengths(lengths);

							puzzles.add(currentPuzzle);
							currentPuzzle = null;
						} else if ("Colors".equals(tagName)) {
							inColors = false;
						} else if ("Items".equals(tagName)) {
							inItems = false;
						}
						break;
				}

				eventType = parser.next();
			}

		} catch (IOException | XmlPullParserException | NumberFormatException e) {
			e.printStackTrace();
		}

		return puzzles;
	}


}
