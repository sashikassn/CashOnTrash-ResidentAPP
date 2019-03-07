package com.slash.cashontrash_residentapp;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.NavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.bumptech.glide.load.engine.Resource;
import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.gson.Gson;
import com.slash.cashontrash_residentapp.Common.Common;
import com.slash.cashontrash_residentapp.Helper.CustomInfoWindow;
import com.slash.cashontrash_residentapp.Model.FCMResponse;
import com.slash.cashontrash_residentapp.Model.Notification;
import com.slash.cashontrash_residentapp.Model.Resident;
import com.slash.cashontrash_residentapp.Model.Sender;
import com.slash.cashontrash_residentapp.Model.Token;
import com.slash.cashontrash_residentapp.Remote.IFCMService;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class  Home extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener, OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {

    SupportMapFragment mapFragment;
    //AutocompleteSupportFragment mapFragment;


    //location
    private GoogleMap mMap;
    private static final int MY_PERMISSION_REQUEST_CODE = 7192;
    private static final int PLAY_SERVICE_RESOLUTION_REQUEST = 300193;

    private LocationRequest mLocationRequest;
    private GoogleApiClient mGoogleApiClient;
    private Location mLastLocation;


    private static int UPDATE_INTERVAL = 5000;
    private static int FATEST_INTERVAL = 3000;
    private static int DISPLACEMENT = 10;


    DatabaseReference ref;
    GeoFire geoFire;

    Marker mUserMarker;

    //BottomSheet
    ImageView imgExpandable;
    BottomSheetCollectorFragment mBottomSheet;

    Button btnPickupRequest;


    boolean isCollectorFound=false;
    String collectorId =  "";
    int radius = 1;  //1km

    int distance = 1;

     private static final int LIMIT = 10;


     //Send Alert

    IFCMService mService;







    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode){
            case MY_PERMISSION_REQUEST_CODE:
                if(grantResults.length >0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    if(checkPlayservices()){
                        buildGoogleApiClient();
                        createLocationRequest();
                            displayLocation();

                    }
                }
                break;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);


        mService = Common.getIFCMService();

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        //maps

        mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
//        mapFragment.setPlaceFields(Arrays.asList(Place.Field.ID,Place.Field.NAME));

        mapFragment.getMapAsync(this);




        //init view
        imgExpandable = (ImageView) findViewById(R.id.imgExpandable);
        //mBottomSheet = BottomSheetCollectorFragment.newInstance("Collector bottom sheet");
            mBottomSheet = (BottomSheetCollectorFragment) BottomSheetCollectorFragment.newInstance("Collector bottom sheet");
            imgExpandable.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {

                    mBottomSheet.show(getSupportFragmentManager(),mBottomSheet.getTag());

                }
            });

            btnPickupRequest = (Button) findViewById(R.id.btnPickupRequest);
            btnPickupRequest.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {

                    if(!isCollectorFound)
                        requestPickupHere(FirebaseAuth.getInstance().getCurrentUser().getUid());
                    else
                        sendRequesttoCollector(collectorId);
                    
                }
            });

        setUpLocation();

        updateFirebaseToken();

    }

    private void updateFirebaseToken() {

        FirebaseDatabase db = FirebaseDatabase.getInstance();
        DatabaseReference tokens = db.getReference(Common.token_tbl);


        Token token = new Token(FirebaseInstanceId.getInstance().getToken());
        tokens.child(FirebaseAuth.getInstance().getCurrentUser().getUid())
                .setValue(token);
    }

    private void sendRequesttoCollector(String collectorId) {

        DatabaseReference tokens = FirebaseDatabase.getInstance().getReference(Common.token_tbl);

        tokens.orderByKey().equalTo(collectorId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        for(DataSnapshot postSnapShot:dataSnapshot.getChildren()){
                            Token token = postSnapShot.getValue(Token.class); //getting token object from db with key


                            String json_lat_lng = new Gson().toJson(new LatLng(mLastLocation.getLatitude(),mLastLocation.getLongitude()));

                            Notification data = new Notification("SLASH",json_lat_lng);
                            Sender content = new Sender(token.getToken(),data);

                            mService.sendMessage(content)
                                    .enqueue(new Callback<FCMResponse>() {
                                        @Override
                                        public void onResponse(Call<FCMResponse> call, Response<FCMResponse> response) {
                                            if(response.body().success == 1)
                                                Toast.makeText(Home.this,"Request Send !",Toast.LENGTH_SHORT).show();
                                            else
                                                Toast.makeText(Home.this,"Failed to send",Toast.LENGTH_SHORT).show();



                                        }

                                        @Override
                                        public void onFailure(Call<FCMResponse> call, Throwable t) {
                                            Log.e("ERROR",t.getMessage());

                                        }
                                    });

                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {

                    }
                });
    }

    private void requestPickupHere(String uid) {

        DatabaseReference dbRequest = FirebaseDatabase.getInstance().getReference(Common.trashpickup_request_tbl);
        GeoFire mGeofire = new GeoFire(dbRequest);
        mGeofire.setLocation(uid,new GeoLocation(mLastLocation.getLatitude(),mLastLocation.getLongitude()));


        if(mUserMarker.isVisible())
            mUserMarker.remove();

        //add new Marker

      mUserMarker=   mMap.addMarker(new MarkerOptions().title("Pick My Trash Here!").snippet("")
        .position(new LatLng(mLastLocation.getLatitude(),mLastLocation.getLongitude()))
        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));

      mUserMarker.showInfoWindow();

      btnPickupRequest.setText("Notifying Your Trash Collector.....");


      findCollector();

    }

    private void findCollector() {

        DatabaseReference trashcollectors = FirebaseDatabase.getInstance().getReference(Common.collector_tbl);
        GeoFire gfcollectors = new GeoFire(trashcollectors);

        GeoQuery geoQuery = gfcollectors.queryAtLocation(new GeoLocation(mLastLocation.getLatitude(),mLastLocation.getLongitude()),radius);
        geoQuery.removeAllListeners();
        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location) {
                        //if collector found
                if(isCollectorFound){
                    isCollectorFound = true;
                    collectorId = key;
                    btnPickupRequest.setText("CALL the TRASH COLLECTOR");
                    Toast.makeText(Home.this,""+key,Toast.LENGTH_SHORT).show();

                }
            }

            @Override
            public void onKeyExited(String key) {

            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {

            }

            @Override
            public void onGeoQueryReady() {
                //if not collector found near, increase the distance
                if (!isCollectorFound){
                    radius++;
                    findCollector();

                }
            }

            @Override
            public void onGeoQueryError(DatabaseError error) {

            }
        });
    }


    private void setUpLocation() {

        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            //Request Runtime permissions
            ActivityCompat.requestPermissions(this,new String[]{
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
            },MY_PERMISSION_REQUEST_CODE);
        }  else {

            if(checkPlayservices()){
                buildGoogleApiClient();
                createLocationRequest();
                    displayLocation();

            }
        }
    }

    private void displayLocation() {
        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            return;
        }
        mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);

        if(mLastLocation !=null){
                final double latitude = mLastLocation.getLatitude();
                final double longitude = mLastLocation.getLongitude();


//                //Update to the Firebase
//                geoFire.setLocation(FirebaseAuth.getInstance().getCurrentUser().getUid(), new GeoLocation(latitude, longitude), new GeoFire.CompletionListener() {
//                    @Override
//                    public void onComplete(String key, DatabaseError error) {


                        //Add Marker
                        if(mUserMarker != null)
                            mUserMarker.remove();  //remove already marker

                        mUserMarker = mMap.addMarker(new MarkerOptions().position(new LatLng(latitude,longitude)).title("Your Location").icon(BitmapDescriptorFactory.fromResource(R.drawable.icon)));

                        //Move Camera to the position
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(latitude,longitude),15.0f));

//                    }
//                });
                            loadAllAvailableCollectors();



                Log.d("SLASH",String.format("Your Location was changed : %f / %f",latitude,longitude));

        }
        else {
            Log.d("ERROR","Cannot get your Location !");

        }
    }

    private void loadAllAvailableCollectors() {
        //load all collectors around 3km

        DatabaseReference collectorlocation = FirebaseDatabase.getInstance().getReference(Common.collector_tbl);
        GeoFire gf = new GeoFire(collectorlocation);

        GeoQuery geoQuery = gf.queryAtLocation(new GeoLocation(mLastLocation.getLatitude(),mLastLocation.getLongitude()),distance);
        geoQuery.removeAllListeners();


        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, final GeoLocation location) {

                //use key to get email from the table users
                //table users is table when collector register account an update information
                //open collector to check table name

                FirebaseDatabase.getInstance().getReference(Common.user_collector_tbl)
                        .child(key).addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        Resident resident = dataSnapshot.getValue(Resident.class);


                        //add collector to the map
                        mMap.addMarker(new MarkerOptions().position(new LatLng(location.latitude,location.longitude))
                        .flat(true).title(resident.getName()).snippet("Phone: "+resident.getPhone()));
//                        .icon(BitmapDescriptorFactory.fromResource(R.drawable.truck))

                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {

                    }
                });
            }

            @Override
            public void onKeyExited(String key) {

            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {

            }

            @Override
            public void onGeoQueryReady() {
                if (distance <=LIMIT )//3km distance
                {
                    distance++;
                    loadAllAvailableCollectors();

                }
            }

            @Override
            public void onGeoQueryError(DatabaseError error) {

            }
        });

    }

    private void createLocationRequest() {

        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(UPDATE_INTERVAL);
        mLocationRequest.setFastestInterval(FATEST_INTERVAL);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setSmallestDisplacement(DISPLACEMENT);
    }

    private void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        mGoogleApiClient.connect();

    }

    private boolean checkPlayservices() {

        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if(resultCode != ConnectionResult.SUCCESS){
            if(GooglePlayServicesUtil.isUserRecoverableError(resultCode))
                GooglePlayServicesUtil.getErrorDialog(resultCode,this,PLAY_SERVICE_RESOLUTION_REQUEST).show();
            else {
                Toast.makeText(this,"This device is not Supported.",Toast.LENGTH_SHORT).show();
                finish();

            }
            return false;


        }
        return true;
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.home, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_camera) {
            // Handle the camera action
        } else if (id == R.id.nav_gallery) {

        } else if (id == R.id.nav_slideshow) {

        } else if (id == R.id.nav_manage) {

        } else if (id == R.id.nav_share) {

        } else if (id == R.id.nav_send) {

        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {


        try{
            boolean isSuccess = googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this,R.raw.my_map_style));

            if(!isSuccess)
                Log.e("ERROR","Map style load failed");
        }
        catch (Resources.NotFoundException ex){
            ex.printStackTrace();
        }


        mMap = googleMap;
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setZoomGesturesEnabled(true);
        mMap.setInfoWindowAdapter(new CustomInfoWindow(this));



    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
                displayLocation();
                startLocationUpdates();
                

    }

    private void startLocationUpdates() {
        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            return;
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient,mLocationRequest,this);
    }

    @Override
    public void onConnectionSuspended(int i) {
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    public void onLocationChanged(Location location) {

        mLastLocation = location;
        displayLocation();


    }
}
